package bzh.toolapp.apps.remisecascade.web;

import bzh.toolapp.apps.remisecascade.service.pricelist.PriceListConstants;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;
import java.math.BigDecimal;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SaleOrderLineController {

  private final Logger logger = LoggerFactory.getLogger(SaleOrderLineService.class);

  /**
   * Overrides method "compute" from SaleOrderLineController in project axelor-sale.
   *
   * @param request
   * @param response
   */
  public void compute(final ActionRequest request, final ActionResponse response) {
    final Context context = request.getContext();

    final SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);

    final SaleOrder saleOrder = Beans.get(SaleOrderLineService.class).getSaleOrder(context);

    try {
      this.compute(response, saleOrder, saleOrderLine);
    } catch (final Exception e) {
      TraceBackService.trace(response, e);
    }
  }

  private void compute(
      final ActionResponse response, final SaleOrder saleOrder, final SaleOrderLine orderLine)
      throws AxelorException {

    final Map<String, BigDecimal> map =
        Beans.get(SaleOrderLineService.class).computeValues(saleOrder, orderLine);

    map.put("price", orderLine.getPrice());
    map.put("inTaxPrice", orderLine.getInTaxPrice());
    map.put("companyCostPrice", orderLine.getCompanyCostPrice());
    map.put("discountAmount", orderLine.getDiscountAmount());

    response.setValues(map);
    response.setAttr(
        "priceDiscounted",
        "hidden",
        map.getOrDefault("priceDiscounted", BigDecimal.ZERO)
                .compareTo(saleOrder.getInAti() ? orderLine.getInTaxPrice() : orderLine.getPrice())
            == 0);
  }

  public void getDiscount(final ActionRequest request, final ActionResponse response) {

    final Context context = request.getContext();
    final SaleOrderLine saleOrderLine = context.asType(SaleOrderLine.class);
    final SaleOrderLineService saleOrderLineService = Beans.get(SaleOrderLineService.class);

    final SaleOrder saleOrder = saleOrderLineService.getSaleOrder(context);

    if ((saleOrder == null) || (saleOrderLine.getProduct() == null)) {
      return;
    }

    try {

      Map<String, Object> discounts;
      if (saleOrderLine.getProduct().getInAti()) {
        discounts =
            saleOrderLineService.getDiscountsFromPriceLists(
                saleOrder,
                saleOrderLine,
                saleOrderLineService.getInTaxUnitPrice(
                    saleOrder, saleOrderLine, saleOrderLine.getTaxLine()));
      } else {
        discounts =
            saleOrderLineService.getDiscountsFromPriceLists(
                saleOrder,
                saleOrderLine,
                saleOrderLineService.getExTaxUnitPrice(
                    saleOrder, saleOrderLine, saleOrderLine.getTaxLine()));
      }

      if (discounts != null) {
        final BigDecimal price = (BigDecimal) discounts.get("price");
        if ((price != null)
            && (price.compareTo(
                    saleOrderLine.getProduct().getInAti()
                        ? saleOrderLine.getInTaxPrice()
                        : saleOrderLine.getPrice())
                != 0)) {
          if (saleOrderLine.getProduct().getInAti()) {
            response.setValue("inTaxPrice", price);
            response.setValue(
                "price",
                saleOrderLineService.convertUnitPrice(true, saleOrderLine.getTaxLine(), price));
          } else {
            response.setValue("price", price);
            response.setValue(
                "inTaxPrice",
                saleOrderLineService.convertUnitPrice(false, saleOrderLine.getTaxLine(), price));
          }
        }

        if ((saleOrderLine.getProduct().getInAti() != saleOrder.getInAti())
            && ((Integer) discounts.get(PriceListConstants.LINE_DISCOUNT_TYPE_SELECT)
                != PriceListLineRepository.AMOUNT_TYPE_PERCENT)) {
          response.setValue(
              "discountAmount",
              saleOrderLineService.convertUnitPrice(
                  saleOrderLine.getProduct().getInAti(),
                  saleOrderLine.getTaxLine(),
                  (BigDecimal) discounts.get(PriceListConstants.LINE_DISCOUNT_AMOUNT)));
        } else {
          response.setValue(
              "discountAmount", discounts.get(PriceListConstants.LINE_DISCOUNT_AMOUNT));
        }
        response.setValue(
            "discountTypeSelect", discounts.get(PriceListConstants.LINE_DISCOUNT_TYPE_SELECT));
        // manage second discount
        this.logger.debug(
            "seconde remise, type de sélection = {}, montant = {}",
            discounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_TYPE_SELECT),
            discounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_AMOUNT));
        if ((saleOrderLine.getProduct().getInAti() != saleOrder.getInAti())
            && ((Integer) discounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_TYPE_SELECT)
                != PriceListLineRepository.AMOUNT_TYPE_PERCENT)) {
          response.setValue(
              "secDiscountAmount",
              saleOrderLineService.convertUnitPrice(
                  saleOrderLine.getProduct().getInAti(),
                  saleOrderLine.getTaxLine(),
                  (BigDecimal) discounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_AMOUNT)));
        } else {
          response.setValue(
              "secDiscountAmount", discounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_AMOUNT));
        }
        response.setValue(
            "secDiscountTypeSelect",
            discounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_TYPE_SELECT));
      }

    } catch (final Exception e) {
      response.setFlash(e.getMessage());
    }
  }
}
