package bzh.toolapp.apps.remisecascade.service.stockmove;

import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceGeneratorFromSaleOrder;
import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceLineGeneratorSupplyChain;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceLineRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.businessproject.service.ProjectStockMoveInvoiceServiceImpl;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.purchase.db.PurchaseOrderLine;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.PurchaseOrderInvoiceService;
import com.axelor.apps.supplychain.service.SaleOrderInvoiceService;
import com.axelor.apps.supplychain.service.StockMoveLineServiceSupplychain;
import com.axelor.apps.supplychain.service.config.SupplyChainConfigService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

public class ExtendedProjectStockMoveInvoiceServiceImpl extends ProjectStockMoveInvoiceServiceImpl {

  private final StockMoveLineRepository stockMoveLineRepository;
  private final InvoiceRepository invoiceRepository;
  private final PriceListService priceListService;
  protected InvoiceLineService invoiceLineService;

  @Inject
  public ExtendedProjectStockMoveInvoiceServiceImpl(
      final SaleOrderInvoiceService saleOrderInvoiceService,
      final PurchaseOrderInvoiceService purchaseOrderInvoiceService,
      final StockMoveLineServiceSupplychain stockMoveLineServiceSupplychain,
      final InvoiceRepository invoiceRepository,
      final SaleOrderRepository saleOrderRepo,
      final PurchaseOrderRepository purchaseOrderRepo,
      final StockMoveLineRepository stockMoveLineRepositoryParam,
      final InvoiceLineRepository invoiceLineRepository,
      final SupplyChainConfigService supplyChainConfigService,
      final PriceListService priceListService,
      final InvoiceLineService invoiceLineServiceParam) {
    super(
        saleOrderInvoiceService,
        purchaseOrderInvoiceService,
        stockMoveLineServiceSupplychain,
        invoiceRepository,
        saleOrderRepo,
        purchaseOrderRepo,
        stockMoveLineRepositoryParam,
        invoiceLineRepository,
        supplyChainConfigService);
    this.stockMoveLineRepository = stockMoveLineRepositoryParam;
    this.invoiceRepository = invoiceRepository;
    this.priceListService = priceListService;
    this.invoiceLineService = invoiceLineServiceParam;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Invoice createInvoiceFromOrderlessStockMove(
      final StockMove stockMove, final Map<Long, BigDecimal> qtyToInvoiceMap)
      throws AxelorException {

    final int stockMoveType = stockMove.getTypeSelect();
    int invoiceOperationType;

    if (stockMove.getIsReversion()) {
      if (stockMoveType == StockMoveRepository.TYPE_INCOMING) {
        invoiceOperationType = InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND;
      } else if (stockMoveType == StockMoveRepository.TYPE_OUTGOING) {
        invoiceOperationType = InvoiceRepository.OPERATION_TYPE_SUPPLIER_REFUND;
      } else {
        return null;
      }
    } else {
      if (stockMoveType == StockMoveRepository.TYPE_INCOMING) {
        invoiceOperationType = InvoiceRepository.OPERATION_TYPE_SUPPLIER_PURCHASE;
      } else if (stockMoveType == StockMoveRepository.TYPE_OUTGOING) {
        invoiceOperationType = InvoiceRepository.OPERATION_TYPE_CLIENT_SALE;
      } else {
        return null;
      }
    }

    final InvoiceGenerator invoiceGenerator =
        new ExtendedInvoiceGeneratorFromSaleOrder(
            stockMove, invoiceOperationType, this.priceListService, this.invoiceLineService);

    final Invoice invoice = invoiceGenerator.generate();

    invoiceGenerator.populate(
        invoice,
        this.createInvoiceLines(
            invoice, stockMove, stockMove.getStockMoveLineList(), qtyToInvoiceMap));

    if (invoice != null) {

      this.extendInternalReference(stockMove, invoice);
      invoice.setAddressStr(
          Beans.get(AddressService.class).computeAddressStr(invoice.getAddress()));
      if (stockMoveType == StockMoveRepository.TYPE_OUTGOING) {
        invoice.setHeadOfficeAddress(stockMove.getPartner().getHeadOfficeAddress());
      }
      this.invoiceRepository.save(invoice);

      if (invoice != null) {
        Set<StockMove> stockMoveSet = invoice.getStockMoveSet();
        if (stockMoveSet == null) {
          stockMoveSet = new HashSet<>();
          invoice.setStockMoveSet(stockMoveSet);
        }
        stockMoveSet.add(stockMove);
      }

      this.invoiceRepository.save(invoice);
    }

    return invoice;
  }

  @Override
  public InvoiceLine createInvoiceLine(
      final Invoice invoice, final StockMoveLine stockMoveLine, final BigDecimal qty)
      throws AxelorException {

    final Product product = stockMoveLine.getProduct();
    boolean isTitleLine = false;

    int sequence = InvoiceLineGenerator.DEFAULT_SEQUENCE;
    final SaleOrderLine saleOrderLine = stockMoveLine.getSaleOrderLine();
    final PurchaseOrderLine purchaseOrderLine = stockMoveLine.getPurchaseOrderLine();

    if (saleOrderLine != null) {
      sequence = saleOrderLine.getSequence();
    } else if (purchaseOrderLine != null) {
      if (purchaseOrderLine.getIsTitleLine()) {
        isTitleLine = true;
      }
      sequence = purchaseOrderLine.getSequence();
    }

    // do not create lines with no qties
    if (((qty == null) || (qty.signum() == 0) || (stockMoveLine.getRealQty().signum() == 0))
        && !isTitleLine) {
      return null;
    }
    if ((product == null) && !isTitleLine) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.STOCK_MOVE_INVOICE_1),
          stockMoveLine.getStockMove().getStockMoveSeq());
    }

    final InvoiceLineGenerator invoiceLineGenerator =
        new ExtendedInvoiceLineGeneratorSupplyChain(
            invoice,
            product,
            stockMoveLine.getProductName(),
            stockMoveLine.getDescription(),
            qty,
            stockMoveLine.getUnit(),
            sequence,
            false,
            stockMoveLine.getSaleOrderLine(),
            stockMoveLine.getPurchaseOrderLine(),
            stockMoveLine,
            this.priceListService);

    final List<InvoiceLine> invoiceLines = invoiceLineGenerator.creates();
    InvoiceLine invoiceLine = null;
    if ((invoiceLines != null) && !invoiceLines.isEmpty()) {
      invoiceLine = invoiceLines.get(0);
      if (!stockMoveLine.getIsMergedStockMoveLine()) {
        // not a consolidated line so we can set the reference.
        invoiceLine.setStockMoveLine(stockMoveLine);
      } else {
        // set the reference to a correct stock move line by following either the sale
        // order line or
        // purchase order line. We cannot have a consolidated line without purchase
        // order line or
        // sale order line reference
        StockMoveLine nonConsolidatedStockMoveLine = null;
        final StockMove stockMove = stockMoveLine.getStockMove();
        if (saleOrderLine != null) {
          nonConsolidatedStockMoveLine =
              this.stockMoveLineRepository
                  .all()
                  .filter(
                      "self.saleOrderLine.id = :saleOrderLineId "
                          + "AND self.stockMove.id = :stockMoveId "
                          + "AND self.id != :stockMoveLineId")
                  .bind("saleOrderLineId", saleOrderLine.getId())
                  .bind("stockMoveId", stockMove.getId())
                  .bind("stockMoveLineId", stockMoveLine.getId())
                  .order("id")
                  .fetchOne();
        } else if (purchaseOrderLine != null) {
          nonConsolidatedStockMoveLine =
              this.stockMoveLineRepository
                  .all()
                  .filter(
                      "self.purchaseOrderLine.id = :purchaseOrderLineId "
                          + "AND self.stockMove.id = :stockMoveId "
                          + "AND self.id != :stockMoveLineId")
                  .bind("purchaseOrderLineId", purchaseOrderLine.getId())
                  .bind("stockMoveId", stockMove.getId())
                  .bind("stockMoveLineId", stockMoveLine.getId())
                  .order("id")
                  .fetchOne();
        }
        invoiceLine.setStockMoveLine(nonConsolidatedStockMoveLine);
        this.deleteConsolidatedStockMoveLine(stockMoveLine);
      }
    }

    if ((invoiceLine == null)
        || !Beans.get(AppBusinessProjectService.class).isApp("business-project")) {
      return invoiceLine;
    }

    if (saleOrderLine != null) {
      invoiceLine.setProject(saleOrderLine.getProject());
    }

    if (purchaseOrderLine != null) {
      invoiceLine.setProject(purchaseOrderLine.getProject());
    }

    return invoiceLine;
  }
}
