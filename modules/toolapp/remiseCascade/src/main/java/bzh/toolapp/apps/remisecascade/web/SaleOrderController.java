package bzh.toolapp.apps.remisecascade.web;

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
}
