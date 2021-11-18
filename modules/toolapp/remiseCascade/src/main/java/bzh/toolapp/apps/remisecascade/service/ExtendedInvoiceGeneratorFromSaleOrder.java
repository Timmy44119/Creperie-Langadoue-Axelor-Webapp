package bzh.toolapp.apps.remisecascade.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceGeneratorSupplyChain;
import com.axelor.exception.AxelorException;

/**
 * To generate Invoice from Sales order.
 *
 */
public class ExtendedInvoiceGeneratorFromSaleOrder extends InvoiceGeneratorSupplyChain {

	private final PriceListService priceListService;

	protected ExtendedInvoiceGeneratorFromSaleOrder(final SaleOrder saleOrder, final boolean isRefund,
			final PriceListService priceListServiceParam) throws AxelorException {
		super(saleOrder, isRefund);
		this.priceListService = priceListServiceParam;
	}

	@Override
	public Invoice generate() throws AxelorException {
		final Invoice invoiceResult = super.createInvoiceHeader();
		invoiceResult.setHeadOfficeAddress(this.saleOrder.getClientPartner().getHeadOfficeAddress());

		invoiceResult.setDiscountAmount(this.saleOrder.getDiscountAmount());
		invoiceResult.setDiscountTypeSelect(this.saleOrder.getDiscountTypeSelect());
		invoiceResult.setSecDiscountAmount(this.saleOrder.getSecDiscountAmount());
		invoiceResult.setSecDiscountTypeSelect(this.saleOrder.getSecDiscountTypeSelect());

		return invoiceResult;
	}

	/**
	 * Compute the invoice total amounts
	 *
	 * @param invoice
	 * @throws AxelorException
	 */
	@Override
	public void computeInvoice(final Invoice invoice) throws AxelorException {
		// reuse modified algorithm (avoid duplication)
		final ExtendedInvoiceGeneratorFromScratch invoiceGenerator = new ExtendedInvoiceGeneratorFromScratch(invoice,
				this.priceListService);
		invoiceGenerator.computeInvoice(invoice);
	}


}
