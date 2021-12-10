package bzh.toolapp.apps.remisecascade.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.account.service.invoice.print.InvoicePrintServiceImpl;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.businessproduction.service.SaleOrderLineBusinessProductionServiceImpl;
import com.axelor.apps.businessproject.service.InvoiceLineProjectServiceImpl;
import com.axelor.apps.businessproject.service.ProjectStockMoveInvoiceServiceImpl;
import com.axelor.apps.businessproject.service.SaleOrderInvoiceProjectServiceImpl;
import com.axelor.apps.cash.management.service.InvoiceServiceManagementImpl;
import com.axelor.apps.supplychain.service.SaleOrderComputeServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.StockMoveMultiInvoiceServiceImpl;

import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceLineServiceImpl;
import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoicePrintServiceImpl;
import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceServiceImpl;
import bzh.toolapp.apps.remisecascade.service.pricelist.ExtendedPriceListServiceImpl;
import bzh.toolapp.apps.remisecascade.service.saleorder.ExtendedSaleOrderComputeServiceImpl;
import bzh.toolapp.apps.remisecascade.service.saleorder.ExtendedSaleOrderInvoiceServiceImpl;
import bzh.toolapp.apps.remisecascade.service.saleorder.ExtendedSaleOrderLineServiceImpl;
import bzh.toolapp.apps.remisecascade.service.stockmove.ExtendedProjectStockMoveInvoiceServiceImpl;
import bzh.toolapp.apps.remisecascade.service.stockmove.ExtendedStockMoveMultiInvoiceServiceImpl;

public class RemiseCascadeModule extends AxelorModule {

	@Override
	protected void configure() {
		this.bind(SaleOrderComputeServiceSupplychainImpl.class).to(ExtendedSaleOrderComputeServiceImpl.class);
		this.bind(SaleOrderLineBusinessProductionServiceImpl.class).to(ExtendedSaleOrderLineServiceImpl.class);
		this.bind(PriceListService.class).to(ExtendedPriceListServiceImpl.class);
		this.bind(InvoiceLineProjectServiceImpl.class).to(ExtendedInvoiceLineServiceImpl.class);
		this.bind(InvoiceServiceManagementImpl.class).to(ExtendedInvoiceServiceImpl.class);
		this.bind(SaleOrderInvoiceProjectServiceImpl.class).to(ExtendedSaleOrderInvoiceServiceImpl.class);
		this.bind(ProjectStockMoveInvoiceServiceImpl.class).to(ExtendedProjectStockMoveInvoiceServiceImpl.class);
		this.bind(InvoicePrintServiceImpl.class).to(ExtendedInvoicePrintServiceImpl.class);
		this.bind(StockMoveMultiInvoiceServiceImpl.class).to(ExtendedStockMoveMultiInvoiceServiceImpl.class);
	}
}
