package bzh.toolapp.apps.remisecascade.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceGeneratorSupplyChain;
import com.axelor.exception.AxelorException;

public class ExtendedInvoiceGeneratorSupplyChain extends InvoiceGeneratorSupplyChain {

	protected ExtendedInvoiceGeneratorSupplyChain(final SaleOrder saleOrder, final boolean isRefund)
			throws AxelorException {
		super(saleOrder, isRefund);
	}

	@Override
	public Invoice generate() throws AxelorException {
		final Invoice invoice = super.createInvoiceHeader();
		invoice.setHeadOfficeAddress(this.saleOrder.getClientPartner().getHeadOfficeAddress());

		invoice.setDiscountAmount(this.saleOrder.getDiscountAmount());
		invoice.setDiscountTypeSelect(this.saleOrder.getDiscountTypeSelect());
		invoice.setSecDiscountAmount(this.saleOrder.getSecDiscountAmount());
		invoice.setSecDiscountTypeSelect(this.saleOrder.getSecDiscountTypeSelect());
		return invoice;
	}

}
