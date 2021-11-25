package bzh.toolapp.apps.remisecascade.web;

import java.math.BigDecimal;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PartnerPriceListService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.sale.service.saleorder.SaleOrderComputeService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.exception.service.TraceBackService;
import com.axelor.inject.Beans;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.google.inject.Singleton;

@Singleton
public class SaleOrderController {
	private final Logger logger = LoggerFactory.getLogger(SaleOrderLineService.class);

	public void compute(final ActionRequest request, final ActionResponse response) {
		SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

		try {
			saleOrder = Beans.get(SaleOrderComputeService.class).computeSaleOrder(saleOrder);
			response.setValues(saleOrder);
		} catch (final Exception e) {
			TraceBackService.trace(response, e);
		}
	}

	public void propagatePriceListDiscounts(final ActionRequest request, final ActionResponse response) {
		final SaleOrder saleOrder = request.getContext().asType(SaleOrder.class);

		final PriceList priceList = saleOrder.getPriceList();

		if (priceList != null) {
			this.logger.debug("La price list est {}", priceList.getTitle());
			// override global discount information
			saleOrder.setDiscountAmount(priceList.getGeneralDiscount());
			saleOrder.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
			saleOrder.setSecDiscountAmount(priceList.getSecGeneralDiscount());
			saleOrder.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
		} else {
			// override global discount information
			saleOrder.setDiscountAmount(BigDecimal.ZERO);
			saleOrder.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
			saleOrder.setSecDiscountAmount(BigDecimal.ZERO);
			saleOrder.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
		}
		// send updated element to view
		response.setValues(saleOrder);
	}

	/**
	 * Called from sale order form view on partner change. Get the default price
	 * list for the sale order. Call
	 * {@link PartnerPriceListService#getDefaultPriceList(Partner, int)}.
	 *
	 * @param request
	 * @param response
	 */
	@SuppressWarnings("unchecked")
	public void fillPriceList(final ActionRequest request, final ActionResponse response) {
		SaleOrder saleOrder;

		if (request.getContext().get("_saleOrderTemplate") != null) {
			final LinkedHashMap<String, Object> saleOrderTemplateContext = (LinkedHashMap<String, Object>) request
					.getContext().get("_saleOrderTemplate");
			final Integer saleOrderId = (Integer) saleOrderTemplateContext.get("id");
			saleOrder = Beans.get(SaleOrderRepository.class).find(Long.valueOf(saleOrderId));
		} else {
			saleOrder = request.getContext().asType(SaleOrder.class);
		}

		// If a client partner is add
		if (saleOrder.getClientPartner() != null) {
			// Select the price list
			final PriceList priceList = Beans.get(PartnerPriceListService.class)
					.getDefaultPriceList(saleOrder.getClientPartner(), PriceListRepository.TYPE_SALE);

			// The price list affectation
			saleOrder.setPriceList(priceList);

			if (priceList == null) {
				// override global discount information
				saleOrder.setDiscountAmount(BigDecimal.ZERO);
				saleOrder.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
				saleOrder.setSecDiscountAmount(BigDecimal.ZERO);
				saleOrder.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);

			} else {
				// The discounts informations affectation
				saleOrder.setDiscountAmount(priceList.getGeneralDiscount());
				saleOrder.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
				saleOrder.setSecDiscountAmount(priceList.getSecGeneralDiscount());
				saleOrder.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
			}
		}

		response.setValues(saleOrder);
	}

}
