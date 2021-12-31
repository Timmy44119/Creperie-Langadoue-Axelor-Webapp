package bzh.toolapp.apps.remisecascade.service.saleorder;

import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceGeneratorFromSaleOrder;
import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceLineGeneratorSupplyChain;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.InvoiceLineGenerator;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businessproject.service.SaleOrderInvoiceProjectServiceImpl;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.apps.sale.service.saleorder.SaleOrderWorkflowServiceImpl;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import java.math.BigDecimal;
import java.util.List;
import javax.inject.Inject;

public class ExtendedSaleOrderInvoiceServiceImpl extends SaleOrderInvoiceProjectServiceImpl {
  private final PriceListService priceListService;
  protected InvoiceLineService invoiceLineService;

  @Inject
  public ExtendedSaleOrderInvoiceServiceImpl(
      final AppBaseService appBaseService,
      final AppSupplychainService appSupplychainService,
      final SaleOrderRepository saleOrderRepo,
      final InvoiceRepository invoiceRepo,
      final InvoiceService invoiceService,
      final AppBusinessProjectService appBusinessProjectService,
      final StockMoveRepository stockMoveRepository,
      final SaleOrderLineService saleOrderLineService,
      final SaleOrderWorkflowServiceImpl saleOrderWorkflowServiceImpl,
      final PriceListService priceListServiceParam,
      final InvoiceLineService invoiceLineServiceParam) {
    super(
        appBaseService,
        appSupplychainService,
        saleOrderRepo,
        invoiceRepo,
        invoiceService,
        appBusinessProjectService,
        stockMoveRepository,
        saleOrderLineService,
        saleOrderWorkflowServiceImpl);
    this.priceListService = priceListServiceParam;
    this.invoiceLineService = invoiceLineServiceParam;
  }

  @Override
  public InvoiceGenerator createInvoiceGenerator(final SaleOrder saleOrder, final boolean isRefund)
      throws AxelorException {
    if (saleOrder.getCurrency() == null) {
      throw new AxelorException(
          saleOrder,
          TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
          I18n.get(IExceptionMessage.SO_INVOICE_6),
          saleOrder.getSaleOrderSeq());
    }

    return new ExtendedInvoiceGeneratorFromSaleOrder(
        saleOrder, isRefund, this.priceListService, this.invoiceLineService);
  }

  @Override
  public List<InvoiceLine> createInvoiceLine(
      final Invoice invoice, final SaleOrderLine saleOrderLine, final BigDecimal qtyToInvoice)
      throws AxelorException {

    final Product product = saleOrderLine.getProduct();

    final InvoiceLineGenerator invoiceLineGenerator =
        new ExtendedInvoiceLineGeneratorSupplyChain(
            invoice,
            product,
            saleOrderLine.getProductName(),
            saleOrderLine.getDescription(),
            qtyToInvoice,
            saleOrderLine.getUnit(),
            saleOrderLine.getSequence(),
            false,
            saleOrderLine,
            null,
            null,
            this.priceListService);

    final List<InvoiceLine> invoiceLines = invoiceLineGenerator.creates();

    if (!Beans.get(AppBusinessProjectService.class).isApp("business-project")) {
      return invoiceLines;
    }

    for (final InvoiceLine invoiceLine : invoiceLines) {
      if (saleOrderLine != null) {
        invoiceLine.setProject(saleOrderLine.getProject());
      }
    }

    return invoiceLines;
  }
}
