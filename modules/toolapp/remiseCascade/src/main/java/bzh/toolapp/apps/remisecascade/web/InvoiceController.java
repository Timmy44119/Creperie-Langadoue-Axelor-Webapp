package bzh.toolapp.apps.remisecascade.web;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class InvoiceController {
  /**
   * Same method content than InvoiceController in project axelor-account. Naybe not needed as we
   * override InvoiceService ...
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

  public void propagatePriceListDiscounts(
      final ActionRequest request, final ActionResponse response) {
    final Invoice invoice = request.getContext().asType(Invoice.class);

    final PriceList priceList = invoice.getPriceList();
    if (priceList != null) {
      // override global discount information
      invoice.setDiscountAmount(priceList.getGeneralDiscount());
      invoice.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
      invoice.setSecDiscountAmount(priceList.getSecGeneralDiscount());
      invoice.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
      // send updated element to view
      response.setValues(invoice);
    }
  }
}
