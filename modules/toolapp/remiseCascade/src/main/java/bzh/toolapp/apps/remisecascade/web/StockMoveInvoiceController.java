package bzh.toolapp.apps.remisecascade.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.service.StockMoveMultiInvoiceService;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.actions.ActionView.ActionViewBuilder;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.google.inject.Singleton;

@Singleton
public class StockMoveInvoiceController {

	private final Logger logger = LoggerFactory.getLogger(SaleOrderLineService.class);
	private final String CUSTOMER_STOCK_MOVE_TO_INVOICE = "customerStockMoveToInvoice";
	private final String ORIGIN_TYPE_SELECT_NULL = "NULL";

	public void generateInvoice(final ActionRequest request, final ActionResponse response) {
		try {

			Boolean singleDocument = false;
			// Get Context
			final Context context = request.getContext();

			// Get detail of stockMoves selected
			final List<Map> stockMoveMap = (List<Map>) context.get(this.CUSTOMER_STOCK_MOVE_TO_INVOICE);
			final List<StockMove> stockMoves = this.stockMovesDetail(stockMoveMap);

			// Sort the stock moves by the sales orders numbers
			this.sortList(stockMoves);

			// Get partner Id
			final List<Long> partnerId = this.partnerIds(stockMoves);
			this.logger.debug("Partner Id liste le nombre est : {}", partnerId.size());
			// Loop on partner to create invoice
			for (final Long id : partnerId) {
				this.logger.debug("Boucle sur les partner ID");

				// Define Stock move without sale order
				final List<StockMove> stockMovesWoSo = this.getStockMoves(this.ORIGIN_TYPE_SELECT_NULL, id, stockMoves,
						singleDocument);

				// Get list of id of stock move without sale order
				final List<Long> stockMovesWoSoIdList = this.getStockMovesIdList(stockMovesWoSo);

				this.logger.debug("le size de la liste avec commande est de {} ", stockMovesWoSo.size());

				// Sending the list of stock move to be created
				if (stockMovesWoSo.size() != 0) {
					this.sendToInvoice(response, stockMovesWoSo, stockMovesWoSoIdList);
				}

				singleDocument = true;

				// Define stock move with saleOrder
				final List<StockMove> stockMovesWSo = this.getStockMoves(StockMoveRepository.ORIGIN_SALE_ORDER, id,
						stockMoves, singleDocument);

				this.logger.debug("le size de la liste avec commande est de {} ", stockMovesWSo.size());
				// Get list of id of stock move without sale order
				final List<Long> stockMovesWSoIdList = this.getStockMovesIdList(stockMovesWSo);

				// Sending the list of stock move to be created
				if (stockMovesWSo.size() != 0) {
					this.sendToInvoice(response, stockMovesWSo, stockMovesWSoIdList);
				}
			}
		} catch (final Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	// Sorting list by origin number
	public void sortList(final List<StockMove> list) {
		list.sort((obj1, obj2) -> obj1.getOriginId().compareTo(obj2.getOriginId()));
	}

	private List<Long> getStockMovesIdList(final List<StockMove> stockMoves) {
		final List<Long> stockMoveIdList = new ArrayList<>();
		// Collect Id of stock move
		for (final StockMove sm : stockMoves) {
			stockMoveIdList.add(sm.getId());
		}

		return stockMoveIdList;
	}

	// Collect the stock moves
	private List<StockMove> getStockMoves(final String typeStockMove, final Long idPartner,
			final List<StockMove> stockMovesList, final Boolean singleDocument) {

		final List<StockMove> stockMovesToInvoice = new ArrayList<>();

		// Loop on the list
		for (final StockMove sm : stockMovesList) {
			// Same Partner Id
			if (sm.getPartner().getId().equals(idPartner)) {
				final String originTypeSelect;

				// Check if the origin is fill
				if (sm.getOriginTypeSelect() == null) {
					originTypeSelect = this.ORIGIN_TYPE_SELECT_NULL;
				} else {
					originTypeSelect = sm.getOriginTypeSelect();
				}

				// Add the stock move to send to the invoice
				if (originTypeSelect.equals(typeStockMove)) {
					stockMovesToInvoice.add(sm);
					if (singleDocument) {
						break;
					}
				}
			}
		}
		return stockMovesToInvoice;
	}

	// Get partner Id from stock moves
	private List<Long> partnerIds(final List<StockMove> stockMoves) {

		final ArrayList<Long> partnerIdList = new ArrayList<>();

		// Collect partner id
		for (final StockMove stockMove : stockMoves) {
			// Add only if the partner id is unknown
			if (!partnerIdList.contains(stockMove.getPartner().getId())) {
				partnerIdList.add(stockMove.getPartner().getId());
			}
		}

		return partnerIdList;
	}

	// Get Stock moves detail
	private List<StockMove> stockMovesDetail(final List<Map> stockMovesListSelected) {
		final List<Long> stockMoveIdList = new ArrayList<>();
		final List<StockMove> stockMoveList = new ArrayList<>();

		// Get Id of all stockMoves
		for (final Map map : stockMovesListSelected) {
			stockMoveIdList.add(Long.valueOf((Integer) map.get("id")));
		}
		// Get detail of all stock moves
		for (final Long stockMoveId : stockMoveIdList) {
			stockMoveList.add(JPA.em().find(StockMove.class, stockMoveId));
		}
		return stockMoveList;
	}

	/**
	 * Called from mass invoicing out stock move form view. Call method to check for
	 * missing fields. If there are missing fields, show a wizard. Else call
	 * {@link StockMoveMultiInvoiceService#createInvoiceFromMultiOutgoingStockMove(List)}
	 * and show the generated invoice.
	 *
	 * @param request
	 * @param response
	 * @return
	 * @throws AxelorException
	 */
	public void sendToInvoice(final ActionResponse response, final List<StockMove> stockMoveList,
			final List<Long> stockMoveIdList) throws AxelorException {
		this.logger.debug("Liste recu");
		final Map<String, Object> mapResult = Beans.get(StockMoveMultiInvoiceService.class)
				.areFieldsConflictedToGenerateCustInvoice(stockMoveList);
		this.logger.debug("Verification des conflits");
		final boolean paymentConditionToCheck = (Boolean) mapResult.getOrDefault("paymentConditionToCheck", false);
		final boolean paymentModeToCheck = (Boolean) mapResult.getOrDefault("paymentModeToCheck", false);
		final boolean contactPartnerToCheck = (Boolean) mapResult.getOrDefault("contactPartnerToCheck", false);
		this.logger.debug("Verification des informations partner");

		final StockMove stockMove = stockMoveList.get(0);
		final Partner partner = stockMove.getPartner();

		this.logger.debug("Recuperation du partner");

		if (paymentConditionToCheck || paymentModeToCheck || contactPartnerToCheck) {
			final ActionViewBuilder confirmView = ActionView.define("StockMove").model(StockMove.class.getName())
					.add("form", "stock-move-supplychain-concat-cust-invoice-confirm-form").param("popup", "true")
					.param("show-toolbar", "false").param("show-confirm", "false").param("popup-save", "false")
					.param("forceEdit", "true");

			if (paymentConditionToCheck) {
				confirmView.context("contextPaymentConditionToCheck", "true");
			} else {
				confirmView.context("paymentCondition", mapResult.get("paymentCondition"));
			}

			if (paymentModeToCheck) {
				confirmView.context("contextPaymentModeToCheck", "true");
			} else {
				confirmView.context("paymentMode", mapResult.get("paymentMode"));
			}
			if (contactPartnerToCheck) {
				confirmView.context("contextContactPartnerToCheck", "true");
				confirmView.context("contextPartnerId", partner.getId().toString());
			} else {
				confirmView.context("contactPartner", mapResult.get("contactPartner"));
			}
			/*
			 * confirmView.context("customerStockMoveToInvoice",
			 * Joiner.on(",").join(stockMoveIdList)); response.setView(confirmView.map());
			 */
		} else {
			this.logger.debug("Envoi vers la facturation");

			final Optional<Invoice> invoice = Beans.get(StockMoveMultiInvoiceService.class)
					.createInvoiceFromMultiOutgoingStockMove(stockMoveList);
			/*
			 * invoice.ifPresent(inv ->
			 * response.setView(ActionView.define("Invoice").model(Invoice.class.getName())
			 * .add("grid", "invoice-grid").add("form", "invoice-form")
			 * .param("search-filters", "customer-invoices-filters").param("forceEdit",
			 * "true") .context("_operationTypeSelect", inv.getOperationTypeSelect())
			 * .context("todayDate",
			 * Beans.get(AppSupplychainService.class).getTodayDate(stockMove.getCompany()))
			 * .context("_showRecord", String.valueOf(inv.getId())).map()));
			 */
		}
	}

}
