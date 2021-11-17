package bzh.toolapp.apps.remisecascade.service;

import javax.inject.Inject;

import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.businessproject.service.SaleOrderInvoiceProjectServiceImpl;
import com.axelor.apps.businessproject.service.app.AppBusinessProjectService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.apps.sale.service.saleorder.SaleOrderWorkflowServiceImpl;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.exception.IExceptionMessage;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;

public class ExtendedSaleOrderInvoiceServiceImpl extends SaleOrderInvoiceProjectServiceImpl {
	@Inject
	public ExtendedSaleOrderInvoiceServiceImpl(final AppBaseService appBaseService,
			final AppSupplychainService appSupplychainService, final SaleOrderRepository saleOrderRepo,
			final InvoiceRepository invoiceRepo, final InvoiceService invoiceService,
			final AppBusinessProjectService appBusinessProjectService, final StockMoveRepository stockMoveRepository,
			final SaleOrderLineService saleOrderLineService,
			final SaleOrderWorkflowServiceImpl saleOrderWorkflowServiceImpl) {
		super(appBaseService, appSupplychainService, saleOrderRepo, invoiceRepo, invoiceService,
				appBusinessProjectService, stockMoveRepository, saleOrderLineService, saleOrderWorkflowServiceImpl);

	}

	@Override
	public InvoiceGenerator createInvoiceGenerator(final SaleOrder saleOrder, final boolean isRefund)
			throws AxelorException {
		if (saleOrder.getCurrency() == null) {
			throw new AxelorException(saleOrder, TraceBackRepository.CATEGORY_CONFIGURATION_ERROR,
					I18n.get(IExceptionMessage.SO_INVOICE_6), saleOrder.getSaleOrderSeq());
		}

		return new ExtendedInvoiceGeneratorSupplyChain(saleOrder, isRefund);
	}
}
