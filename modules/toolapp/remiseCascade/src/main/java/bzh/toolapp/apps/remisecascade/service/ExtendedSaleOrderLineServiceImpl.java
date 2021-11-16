package bzh.toolapp.apps.remisecascade.service;

import com.axelor.apps.businessproduction.service.SaleOrderLineBusinessProductionServiceImpl;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import java.math.BigDecimal;

public class ExtendedSaleOrderLineServiceImpl extends SaleOrderLineBusinessProductionServiceImpl
    implements SaleOrderLineService {

  @Override
  public BigDecimal computeDiscount(final SaleOrderLine saleOrderLine, final Boolean inAti) {

    final BigDecimal price = inAti ? saleOrderLine.getInTaxPrice() : saleOrderLine.getPrice();

    // compute first discount
    final BigDecimal firstDiscount =
        this.priceListService.computeDiscount(
            price, saleOrderLine.getDiscountTypeSelect(), saleOrderLine.getDiscountAmount());
    // then second discount
    final BigDecimal secondDiscount =
        this.priceListService.computeDiscount(
            firstDiscount,
            saleOrderLine.getSecDiscountTypeSelect(),
            saleOrderLine.getSecDiscountAmount());

    return secondDiscount;
  }
}
