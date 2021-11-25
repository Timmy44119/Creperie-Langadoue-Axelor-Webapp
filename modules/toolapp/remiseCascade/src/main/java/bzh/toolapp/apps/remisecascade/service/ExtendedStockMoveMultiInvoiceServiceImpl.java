package bzh.toolapp.apps.remisecascade.service;

import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.service.StockMoveInvoiceService;
import com.axelor.apps.supplychain.service.StockMoveMultiInvoiceServiceImpl;

public class ExtendedStockMoveMultiInvoiceServiceImpl extends StockMoveMultiInvoiceServiceImpl {

	private final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

	protected InvoiceRepository invoiceRepository;
	protected SaleOrderRepository saleOrderRepository;
	protected PurchaseOrderRepository purchaseOrderRepository;
	protected StockMoveInvoiceService stockMoveInvoiceService;

	@Inject
	public ExtendedStockMoveMultiInvoiceServiceImpl(final InvoiceRepository invoiceRepository,
			final SaleOrderRepository saleOrderRepository, final PurchaseOrderRepository purchaseOrderRepository,
			final StockMoveInvoiceService stockMoveInvoiceService) {
		super(invoiceRepository, saleOrderRepository, purchaseOrderRepository, stockMoveInvoiceService);
		this.invoiceRepository = invoiceRepository;
		this.saleOrderRepository = saleOrderRepository;
		this.purchaseOrderRepository = purchaseOrderRepository;
		this.stockMoveInvoiceService = stockMoveInvoiceService;
	}

	/**
	 * Create a dummy invoice to hold fields used to generate the invoice which will
	 * be saved.
	 *
	 * @param stockMove an out stock move.
	 * @return the created dummy invoice.
	 */
	@Override
	protected Invoice createDummyOutInvoice(final StockMove stockMove) {
		final Invoice dummyInvoice = new Invoice();

		if ((stockMove.getOriginId() != null)
				&& StockMoveRepository.ORIGIN_SALE_ORDER.equals(stockMove.getOriginTypeSelect())) {
			final SaleOrder saleOrder = this.saleOrderRepository.find(stockMove.getOriginId());
			dummyInvoice.setCurrency(saleOrder.getCurrency());
			dummyInvoice.setPartner(saleOrder.getClientPartner());
			dummyInvoice.setCompany(saleOrder.getCompany());
			dummyInvoice.setTradingName(saleOrder.getTradingName());
			dummyInvoice.setPaymentCondition(saleOrder.getPaymentCondition());
			dummyInvoice.setPaymentMode(saleOrder.getPaymentMode());
			dummyInvoice.setAddress(saleOrder.getMainInvoicingAddress());
			dummyInvoice.setAddressStr(saleOrder.getMainInvoicingAddressStr());
			dummyInvoice.setContactPartner(saleOrder.getContactPartner());
			dummyInvoice.setPriceList(saleOrder.getPriceList());
			dummyInvoice.setInAti(saleOrder.getInAti());
		} else {

			dummyInvoice.setCurrency(stockMove.getCompany().getCurrency());
			dummyInvoice.setPartner(stockMove.getPartner());
			dummyInvoice.setCompany(stockMove.getCompany());
			dummyInvoice.setTradingName(stockMove.getTradingName());
			dummyInvoice.setAddress(stockMove.getToAddress());
			dummyInvoice.setAddressStr(stockMove.getToAddressStr());

			if (!stockMove.getPartner().getSalePartnerPriceList().getPriceListSet().isEmpty()) {
				// Find the price list of partner
				final Set<PriceList> partnerPriceListSet = stockMove.getPartner().getSalePartnerPriceList()
						.getPriceListSet();

				final PriceList priceList = this.findPriceList(partnerPriceListSet, stockMove.getStockMoveLineList());

				if (priceList != null) {
					this.logger.debug("Le nom de la priceList appliquee est {}", priceList.getTitle());
					dummyInvoice.setPriceList(priceList);
				}
			}
		}
		return dummyInvoice;
	}

	// Define the right price list
	protected PriceList findPriceList(final Set<PriceList> partnerPriceListSet,
			final List<StockMoveLine> stockMoveLineList) {
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
}
