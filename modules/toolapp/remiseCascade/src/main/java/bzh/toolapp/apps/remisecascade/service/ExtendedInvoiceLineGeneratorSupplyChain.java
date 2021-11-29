package bzh.toolapp.apps.remisecascade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import com.axelor.apps.account.db.AnalyticMoveLine;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.generator.line.InvoiceLineManagement;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;

public class ExtendedInvoiceLineGeneratorSupplyChain extends InvoiceLineGeneratorSupplyChain {

	private final PriceListService priceListService;

	public ExtendedInvoiceLineGeneratorSupplyChain(final Invoice invoice, final Product product,
			final String productName, final String description, final BigDecimal qty, final Unit unit,
			final int sequence, final boolean isTaxInvoice, final SaleOrderLine saleOrderLine,
			final PurchaseOrderLine purchaseOrderLine, final StockMoveLine stockMoveLine,
			final PriceListService priceListServiceParam) throws AxelorException {
		super(invoice, product, productName, description, qty, unit, sequence, isTaxInvoice, saleOrderLine,
				purchaseOrderLine, stockMoveLine);
		this.priceListService = priceListServiceParam;
	}

	@Override
	public List<InvoiceLine> creates() throws AxelorException {
		// Create invoice lines
		final InvoiceLine invoiceLine = this.createInvoiceLine();
		final InvoiceLineService invoiceLineService = Beans.get(InvoiceLineService.class);

		// add second discount information
		if (this.saleOrderLine != null) {
			// Add second discount amount
			invoiceLine.setSecDiscountAmount(this.saleOrderLine.getSecDiscountAmount());

			// Add second discount type select
			invoiceLine.setSecDiscountTypeSelect(this.saleOrderLine.getSecDiscountTypeSelect());

		} else if (this.stockMoveLine != null) {

			// If there is a price list
			if (invoiceLine.getInvoice().getPriceList() != null) {

				// Get the invoice price list
				final PriceList priceList = invoiceLine.getInvoice().getPriceList();

				// check if the price list line exist
				if (this.priceListService.getPriceListLine(invoiceLine.getProduct(), invoiceLine.getQty(), priceList,
						invoiceLine.getPrice()) != null) {

					// Get the price list line
					final PriceListLine priceListLine = this.priceListService.getPriceListLine(invoiceLine.getProduct(),
							invoiceLine.getQty(), priceList, invoiceLine.getPrice());

					// if not null
					if (priceListLine != null) {
						// Apply the discount
						invoiceLine.setDiscountTypeSelect(priceListLine.getAmountTypeSelect());
						invoiceLine.setDiscountAmount(priceListLine.getAmount());
						invoiceLine.setSecDiscountAmount(priceListLine.getSecAmount());
						invoiceLine.setSecDiscountTypeSelect(priceListLine.getSecTypeSelect());

						// Reload price determination
						invoiceLine.setPriceSecDiscounted(
								invoiceLineService.computeDiscount(invoiceLine, invoiceLine.getInvoice().getInAti()));
						invoiceLine.setInTaxPrice(invoiceLineService.getInTaxUnitPrice(this.invoice, invoiceLine,
								invoiceLine.getTaxLine(), false));
						// Check if the invoice is in ATI
						if (!invoiceLine.getInvoice().getInAti()) {
							// Calculate the tax total
							invoiceLine.setExTaxTotal(InvoiceLineManagement.computeAmount(invoiceLine.getQty(),
									invoiceLine.getPriceSecDiscounted(), 2));

							invoiceLine.setInTaxTotal(invoiceLine.getExTaxTotal()
									.add(invoiceLine.getExTaxTotal().multiply(invoiceLine.getTaxRate()))
									.setScale(2, RoundingMode.HALF_UP));
						} else {
							invoiceLine.setInTaxTotal(InvoiceLineManagement.computeAmount(invoiceLine.getQty(),
									invoiceLine.getPriceSecDiscounted(), 2));
							invoiceLine.setExTaxTotal(invoiceLine.getInTaxTotal()
									.divide(invoiceLine.getTaxRate().add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP));
						}

					}
				}
			}
		}

		// Fill the Analytics part
		final List<AnalyticMoveLine> analyticMoveLineList = invoiceLineService
				.getAndComputeAnalyticDistribution(invoiceLine, invoiceLine.getInvoice());

		analyticMoveLineList.stream().forEach(invoiceLine::addAnalyticMoveLineListItem);

		final List<InvoiceLine> invoiceLines = new ArrayList<>();
		invoiceLines.add(invoiceLine);

		return invoiceLines;
	}
}
