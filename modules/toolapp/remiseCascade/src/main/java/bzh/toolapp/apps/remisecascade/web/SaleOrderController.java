package bzh.toolapp.apps.remisecascade.web;

import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.service.saleorder.SaleOrderComputeService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class SaleOrderController {
  public void compute(final ActionRequest request, final ActionResponse response) {
    SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

    try {
      saleOrder = Beans.get(SaleOrderComputeService.class).computeSaleOrder(saleOrder);
      response.setValues(saleOrder);
    } catch (final Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  public void propagatePriceListDiscounts(
      final ActionRequest request, final ActionResponse response) {
    final SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

    final PriceList priceList = saleOrder.getPriceList();
    if (priceList != null) {
      // override global discount information
      saleOrder.setDiscountAmount(priceList.getGeneralDiscount());
      saleOrder.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
      saleOrder.setSecDiscountAmount(priceList.getSecGeneralDiscount());
      saleOrder.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
      // send updated element to view
      response.setValues(saleOrder);
    }
  }
}
