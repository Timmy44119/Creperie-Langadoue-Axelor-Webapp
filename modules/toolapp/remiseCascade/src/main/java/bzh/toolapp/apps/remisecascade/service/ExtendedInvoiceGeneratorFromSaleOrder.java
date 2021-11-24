package bzh.toolapp.apps.remisecascade.service;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.supplychain.service.invoice.generator.InvoiceGeneratorSupplyChain;
import com.axelor.exception.AxelorException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** To generate Invoice from Sales order. */
public class ExtendedInvoiceGeneratorFromSaleOrder extends InvoiceGeneratorSupplyChain {

  private final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

  private final PriceListService priceListService;
  private final StockMove stockMove;
  private final SaleOrder saleOrder;
  private final boolean isRefund;

  protected ExtendedInvoiceGeneratorFromSaleOrder(
      final SaleOrder saleOrder,
      final boolean isRefund,
      final PriceListService priceListServiceParam)
      throws AxelorException {
    super(saleOrder, isRefund);
    this.saleOrder = saleOrder;
    this.isRefund = isRefund;
    this.priceListService = priceListServiceParam;
    this.stockMove = null;
  }

  /**
   * PaymentCondition, Paymentmode, MainInvoicingAddress, Currency récupérés du tiers
   *
   * @param operationType
   * @param company
   * @param partner
   * @param contactPartner
   * @throws AxelorException
   */
  protected ExtendedInvoiceGeneratorFromSaleOrder(
      final StockMove stockMove,
      final int invoiceOperationType,
      final PriceListService priceListServiceParam)
      throws AxelorException {
    super(stockMove, invoiceOperationType);
    this.stockMove = stockMove;
    this.priceListService = priceListServiceParam;
    this.saleOrder = null;
    this.isRefund = false;
  }

  @Override
  public Invoice generate() throws AxelorException {
    final Invoice invoiceResult = super.createInvoiceHeader();
    if (this.saleOrder != null) {
      invoiceResult.setHeadOfficeAddress(this.saleOrder.getClientPartner().getHeadOfficeAddress());
      invoiceResult.setDiscountAmount(this.saleOrder.getDiscountAmount());
      invoiceResult.setDiscountTypeSelect(this.saleOrder.getDiscountTypeSelect());
      invoiceResult.setSecDiscountAmount(this.saleOrder.getSecDiscountAmount());
      invoiceResult.setSecDiscountTypeSelect(this.saleOrder.getSecDiscountTypeSelect());
    }

    if (this.stockMove != null) {
      // Select the PriceListSet from partner
      final Set<PriceList> partnerPriceListSet =
          this.stockMove.getPartner().getSalePartnerPriceList().getPriceListSet();

      final PriceList priceList =
          this.findPriceList(partnerPriceListSet, this.stockMove.getStockMoveLineList());

      invoiceResult.setPriceList(priceList);
    }
    return invoiceResult;
  }

  private PriceList findPriceList(
      final Set<PriceList> partnerPriceListSet, final List<StockMoveLine> stockMoveLineList) {
    PriceList priceList = null;

    switch (partnerPriceListSet.size()) {
      case 1:
        // Verify if the priceList is Activated
        final PriceList pl = partnerPriceListSet.iterator().next();
        if (pl.getIsActive()) {
          priceList = pl;
        }
        break;

      default:
        Integer activatedPriceList = 0;
        // Verify how many price list is activate
        for (final PriceList pl1 : partnerPriceListSet) {
          if (pl1.getIsActive()) {
            activatedPriceList++;
            priceList = pl1;
          }
        }
        // If the number of price list is over 1 the price cannot be found
        if (activatedPriceList > 1) {
          priceList = null;
        }

        break;
    }
    return priceList;
  }

  /**
   * Compute the invoice total amounts
   *
   * @param invoice
   * @throws AxelorException
   */
  @Override
  public void computeInvoice(final Invoice invoice) throws AxelorException {
    // reuse modified algorithm (avoid duplication)
    final ExtendedInvoiceGeneratorFromScratch invoiceGenerator =
        new ExtendedInvoiceGeneratorFromScratch(invoice, this.priceListService);
    invoiceGenerator.computeInvoice(invoice);
  }
}
