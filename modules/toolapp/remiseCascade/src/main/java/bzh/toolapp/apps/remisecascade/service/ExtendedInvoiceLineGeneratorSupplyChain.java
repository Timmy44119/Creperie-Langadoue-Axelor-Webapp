package bzh.toolapp.apps.remisecascade.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.TaxLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.Unit;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceLineGeneratorSupplyChain;
import com.axelor.exception.AxelorException;

public class ExtendedInvoiceLineGeneratorSupplyChain extends InvoiceLineGeneratorSupplyChain {

	public ExtendedInvoiceLineGeneratorSupplyChain(final Invoice invoice, final Product product,
			final String productName, final BigDecimal price, final BigDecimal inTaxPrice,
			final BigDecimal priceDiscounted, final String description, final BigDecimal qty, final Unit unit,
			final TaxLine taxLine, final int sequence, final BigDecimal discountAmount, final int discountTypeSelect,
			final BigDecimal exTaxTotal, final BigDecimal inTaxTotal, final boolean isTaxInvoice,
			final SaleOrderLine saleOrderLine, final PurchaseOrderLine purchaseOrderLine,
			final StockMoveLine stockMoveLine) {
		super(invoice, product, productName, price, inTaxPrice, priceDiscounted, description, qty, unit, taxLine,
				sequence, discountAmount, discountTypeSelect, exTaxTotal, inTaxTotal, isTaxInvoice, saleOrderLine,
				purchaseOrderLine, stockMoveLine);
	}

	@Override
	public List<InvoiceLine> creates() throws AxelorException {

		final InvoiceLine invoiceLine = this.createInvoiceLine();

		// add second discount information
		if (this.saleOrderLine != null) {
			invoiceLine.setSecDiscountAmount(this.saleOrderLine.getSecDiscountAmount());
			invoiceLine.setSecDiscountTypeSelect(this.saleOrderLine.getSecDiscountTypeSelect());
		}

		final List<InvoiceLine> invoiceLines = new ArrayList<>();
		invoiceLines.add(invoiceLine);

		return invoiceLines;
	}

}
