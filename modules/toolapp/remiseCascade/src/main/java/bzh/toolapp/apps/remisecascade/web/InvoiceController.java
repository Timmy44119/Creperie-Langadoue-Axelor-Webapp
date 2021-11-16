package bzh.toolapp.apps.remisecascade.web;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class InvoiceController {
	/**
	 * Same method content than InvoiceController in project axelor-account. Naybe
	 * not needed as we override InvoiceService ...
	 *
	 * @param request
	 * @param response
	 * @return
	 */
	public void compute(final ActionRequest request, final ActionResponse response) {

		Invoice invoice = request.getContext().asType(Invoice.class);

		try {
			invoice = Beans.get(InvoiceService.class).compute(invoice);
			response.setValues(invoice);
		} catch (final Exception e) {
			TraceBackService.trace(response, e);
		}
	}
}
