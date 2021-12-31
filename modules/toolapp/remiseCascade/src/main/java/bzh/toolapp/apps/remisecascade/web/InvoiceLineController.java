package bzh.toolapp.apps.remisecascade.web;

import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceLineServiceImpl;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.Map;

@Singleton
public class InvoiceLineController {

  /**
   * Extends existing behavior in project axelor-account to be able to manage two discounts
   * information.
   *
   * @param request
   * @param response
   * @throws AxelorException
   */
  public void compute(final ActionRequest request, final ActionResponse response)
      throws AxelorException {

    Context context = request.getContext();

    final InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

    if (context.getParent().getContextClass() == InvoiceLine.class) {
      context = request.getContext().getParent();
    }

    final Invoice invoice = this.getInvoice(context);

    if ((invoice == null)
        || (invoiceLine.getPrice() == null)
        || (invoiceLine.getInTaxPrice() == null)
        || (invoiceLine.getQty() == null)) {
      return;
    }
    // Application des remises
    try {
      this.compute(response, invoice, invoiceLine);
    } catch (final Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  private void compute(
      final ActionResponse response, final Invoice invoice, final InvoiceLine invoiceLine)
      throws AxelorException {

    final Map<String, BigDecimal> map =
        Beans.get(ExtendedInvoiceLineServiceImpl.class).computeValues(invoice, invoiceLine);

    response.setValues(map);
    if (invoiceLine.getTaxLine() != null) {
      response.setValue("taxCode", invoiceLine.getTaxLine().getTax().getCode());
    }
    response.setAttr(
        "priceDiscounted",
        "hidden",
        map.getOrDefault("priceDiscounted", BigDecimal.ZERO)
                .compareTo(
                    invoice.getInAti() ? invoiceLine.getInTaxPrice() : invoiceLine.getPrice())
            == 0);
  }

  public Invoice getInvoice(final Context context) {

    final Context parentContext = context.getParent();

    Invoice invoice;

    if ((parentContext == null)
        || !parentContext.getContextClass().toString().equals(Invoice.class.toString())) {

      final InvoiceLine invoiceLine = context.asType(InvoiceLine.class);

      invoice = invoiceLine.getInvoice();
    } else {
      invoice = parentContext.asType(Invoice.class);
    }

    return invoice;
  }
}
