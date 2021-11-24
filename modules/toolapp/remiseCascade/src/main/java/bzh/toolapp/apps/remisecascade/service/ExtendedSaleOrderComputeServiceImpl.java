package bzh.toolapp.apps.remisecascade.service;

import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.service.saleorder.SaleOrderComputeService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineTaxService;
import com.axelor.apps.supplychain.service.SaleOrderComputeServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedSaleOrderComputeServiceImpl extends SaleOrderComputeServiceSupplychainImpl
    implements SaleOrderComputeService {

  private final Logger logger = LoggerFactory.getLogger(SaleOrderComputeService.class);

  protected PriceListService priceListService;

  @Inject protected AppBaseService appBaseService;

  @Inject
  public ExtendedSaleOrderComputeServiceImpl(
      final SaleOrderLineService saleOrderLineService,
      final SaleOrderLineTaxService saleOrderLineTaxService,
      final PriceListService priceListServiceParam) {

    super(saleOrderLineService, saleOrderLineTaxService);
    this.priceListService = priceListServiceParam;
  }

  @Override
  public void _computeSaleOrder(final SaleOrder saleOrder) throws AxelorException {
    saleOrder.setExTaxTotal(BigDecimal.ZERO);
    saleOrder.setCompanyExTaxTotal(BigDecimal.ZERO);
    saleOrder.setTaxTotal(BigDecimal.ZERO);
    saleOrder.setInTaxTotal(BigDecimal.ZERO);

    for (final SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {

      // skip title lines in computing total amounts
      if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_TITLE) {
        continue;
      }
      final BigDecimal lineExTaxTotal = saleOrderLine.getExTaxTotal();
      this.logger.debug("Prix HT {} de la ligne.", lineExTaxTotal);

      // In the company accounting currency
      // TODO this computation should be updated too ...
      saleOrder.setCompanyExTaxTotal(
          saleOrder.getCompanyExTaxTotal().add(saleOrderLine.getCompanyExTaxTotal()));

      final BigDecimal intermediateExTaxPrice =
          this.computeGlobalDiscountPerLine(lineExTaxTotal, saleOrder);

      // update global total without taxes
      saleOrder.setExTaxTotal(saleOrder.getExTaxTotal().add(intermediateExTaxPrice));
      final BigDecimal taxLineValue = saleOrderLine.getTaxLine().getValue();
      final BigDecimal taxPrice = intermediateExTaxPrice.multiply(taxLineValue);

      // update also final total of taxes
      saleOrder.setTaxTotal(
          saleOrder
              .getTaxTotal()
              .add(taxPrice)
              .setScale(this.appBaseService.getNbDecimalDigitForUnitPrice()));
      this.logger.debug("montant de la taxe {}", taxPrice);

      // compute price for this line with global discount (HT + taxes)
      final BigDecimal intermediateInTaxPrice = intermediateExTaxPrice.add(taxPrice);
      this.logger.debug(
          "Remise globale appliquée sur le montant de la ligne : HT = {}, TTC = {}",
          intermediateExTaxPrice,
          intermediateInTaxPrice);

      // update also final total with taxes
      saleOrder.setInTaxTotal(
          saleOrder
              .getInTaxTotal()
              .add(intermediateInTaxPrice)
              .setScale(this.appBaseService.getNbDecimalDigitForUnitPrice()));
      this.logger.debug("prix global intermédiaire : TTC = {}", saleOrder.getInTaxTotal());
    }

    saleOrder.setAdvanceTotal(this.computeTotalAdvancePayment(saleOrder));
    this.logger.debug(
        "Montant de la facture: HTT = {},  HT = {}, TTC = {}",
        saleOrder.getExTaxTotal(),
        saleOrder.getTaxTotal(),
        saleOrder.getInTaxTotal());

    // duplicate also supplychain behavior
    if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
      return;
    }

    int maxDelay = 0;

    if ((saleOrder.getSaleOrderLineList() != null) && !saleOrder.getSaleOrderLineList().isEmpty()) {
      for (final SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {

        if (((saleOrderLine.getSaleSupplySelect() == SaleOrderLineRepository.SALE_SUPPLY_PRODUCE)
            || (saleOrderLine.getSaleSupplySelect()
                == SaleOrderLineRepository.SALE_SUPPLY_PURCHASE))) {
          maxDelay =
              Integer.max(
                  maxDelay,
                  saleOrderLine.getStandardDelay() == null ? 0 : saleOrderLine.getStandardDelay());
        }
      }
    }
    saleOrder.setStandardDelay(maxDelay);

    if (Beans.get(AppAccountService.class).getAppAccount().getManageAdvancePaymentInvoice()) {
      saleOrder.setAdvanceTotal(this.computeTotalInvoiceAdvancePayment(saleOrder));
    }
    Beans.get(SaleOrderServiceSupplychainImpl.class)
        .updateAmountToBeSpreadOverTheTimetable(saleOrder);
  }

  private BigDecimal computeGlobalDiscountPerLine(
      final BigDecimal originalPrice, final SaleOrder saleOrder) {
    /*
     * Now, we have to use discount information to update amount without taxes, then
     * compute again final amount with taxes.
     */
    // compute first discount
    final BigDecimal firstDiscount =
        this.priceListService.computeDiscount(
            originalPrice, saleOrder.getDiscountTypeSelect(), saleOrder.getDiscountAmount());

    // then second discount
    final BigDecimal secondDiscount =
        this.priceListService.computeDiscount(
            firstDiscount, saleOrder.getSecDiscountTypeSelect(), saleOrder.getSecDiscountAmount());
    return secondDiscount;
  }
}
