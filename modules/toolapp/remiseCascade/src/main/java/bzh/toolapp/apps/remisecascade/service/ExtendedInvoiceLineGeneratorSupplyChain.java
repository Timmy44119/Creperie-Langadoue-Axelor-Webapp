package bzh.toolapp.apps.remisecascade.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
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

		// add second discount information
		if (this.saleOrderLine != null) {
			// Add second discount amount
			invoiceLine.setSecDiscountAmount(this.saleOrderLine.getSecDiscountAmount());

			// Add second discount type select
			invoiceLine.setSecDiscountTypeSelect(this.saleOrderLine.getSecDiscountTypeSelect());
		}

		if (this.stockMoveLine != null) {
			if (invoiceLine.getInvoice().getPriceList() != null) {
				final PriceList priceList = invoiceLine.getInvoice().getPriceList();
				final PriceListLine priceListLine = this.priceListService.getPriceListLine(invoiceLine.getProduct(),
						invoiceLine.getQty(), priceList, invoiceLine.getPrice());

				if (priceListLine != null) {
					invoiceLine.setDiscountTypeSelect(priceListLine.getAmountTypeSelect());
					invoiceLine.setDiscountAmount(priceListLine.getAmount());
					invoiceLine.setSecDiscountAmount(priceListLine.getSecAmount());
					invoiceLine.setSecDiscountTypeSelect(priceListLine.getSecTypeSelect());
				}
			}
		}

		final List<InvoiceLine> invoiceLines = new ArrayList<>();
		invoiceLines.add(invoiceLine);

		return invoiceLines;
	}
}
