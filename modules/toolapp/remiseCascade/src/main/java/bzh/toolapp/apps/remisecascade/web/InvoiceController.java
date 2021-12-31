package bzh.toolapp.apps.remisecascade.web;

import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceServiceImpl;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class InvoiceController {
  private final Logger logger = LoggerFactory.getLogger(SaleOrderLineService.class);

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

  /**
   * Method permettant la mise � jours des montant de la facture
   *
   * @param request
   * @param response
   */
  public void updateInvoice(final ActionRequest request, final ActionResponse response) {
    // Recup�ration de la facture
    final Invoice invoice = request.getContext().asType(Invoice.class);
    try {
      Beans.get(ExtendedInvoiceServiceImpl.class).updateInvoiceLineList(invoice);
      response.setValue("invoiceLineList", invoice.getInvoiceLineList());
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
    } else {
      // override global discount information
      invoice.setDiscountAmount(BigDecimal.ZERO);
      invoice.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
      invoice.setSecDiscountAmount(BigDecimal.ZERO);
      invoice.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
    }
    // send updated element to view
    response.setValues(invoice);
  }

  /**
   * Called from invoice form view on partner change. Get the default price list for the invoice.
   * Call {@link PartnerPriceListService#getDefaultPriceList(Partner, int)}.
   *
   * @param request
   * @param response
   */
  public void fillPriceList(final ActionRequest request, final ActionResponse response) {
    try {
      final Invoice invoice = request.getContext().asType(Invoice.class);
      final Partner partner = invoice.getPartner();
      if (partner == null) {
        return;
      }
      final int priceListTypeSelect =
          Beans.get(InvoiceService.class).getPurchaseTypeOrSaleType(invoice);

      if (invoice.getPartner() != null) {
        // Select the price list
        final PriceList priceList =
            Beans.get(PartnerPriceListService.class)
                .getDefaultPriceList(partner, priceListTypeSelect);

        // The price List affectation
        invoice.setPriceList(priceList);

        if (priceList != null) {

          // The discounts informations affectation
          invoice.setDiscountAmount(priceList.getGeneralDiscount());
          invoice.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
          invoice.setSecDiscountAmount(priceList.getSecGeneralDiscount());
          invoice.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
        } else {

          // override global discount information
          invoice.setDiscountAmount(BigDecimal.ZERO);
          invoice.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
          invoice.setSecDiscountAmount(BigDecimal.ZERO);
          invoice.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
        }
      }

      response.setValues(invoice);

    } catch (final Exception e) {
      TraceBackService.trace(response, e);
    }
  }
}
