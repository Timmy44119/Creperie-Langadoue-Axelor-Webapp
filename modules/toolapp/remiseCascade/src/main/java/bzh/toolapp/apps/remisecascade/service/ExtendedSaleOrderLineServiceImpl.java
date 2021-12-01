package bzh.toolapp.apps.remisecascade.service;

import java.math.BigDecimal;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.businessproduction.service.SaleOrderLineBusinessProductionServiceImpl;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;

public class ExtendedSaleOrderLineServiceImpl extends SaleOrderLineBusinessProductionServiceImpl
		implements SaleOrderLineService {
	private final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

	@Override
	public BigDecimal computeDiscount(final SaleOrderLine saleOrderLine, final Boolean inAti) {

		final BigDecimal price = inAti ? saleOrderLine.getInTaxPrice() : saleOrderLine.getPrice();

		// compute first discount
		final BigDecimal firstDiscount = this.priceListService.computeDiscount(price,
				saleOrderLine.getDiscountTypeSelect(), saleOrderLine.getDiscountAmount());
		this.logger.debug("Le montant de la premiere remise est de {}", firstDiscount);
		// then second discount
		final BigDecimal secondDiscount = this.priceListService.computeDiscount(firstDiscount,
				saleOrderLine.getSecDiscountTypeSelect(), saleOrderLine.getSecDiscountAmount());

		return secondDiscount;
	}

	@Override
	public Map<String, Object> getDiscountsFromPriceLists(final SaleOrder saleOrder, final SaleOrderLine saleOrderLine,
			final BigDecimal price) {

		Map<String, Object> discounts = null;

		final PriceList priceList = saleOrder.getPriceList();

		if (priceList != null) {
			final PriceListLine priceListLine = this.getPriceListLine(saleOrderLine, priceList, price);
			discounts = this.priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, price);

			// disable manual replacements
		}

		return discounts;
	}

	@Override
	protected BigDecimal fillDiscount(final SaleOrderLine saleOrderLine, final SaleOrder saleOrder, BigDecimal price) {
		final Map<String, Object> discounts = this.getDiscountsFromPriceLists(saleOrder, saleOrderLine, price);

		if (discounts != null) {
			if (discounts.get("price") != null) {
				price = (BigDecimal) discounts.get("price");
			}
			if ((saleOrderLine.getProduct().getInAti() != saleOrder.getInAti()) && ((Integer) discounts.get(
					PriceListConstants.LINE_DISCOUNT_TYPE_SELECT) != PriceListLineRepository.AMOUNT_TYPE_PERCENT)) {
				saleOrderLine.setDiscountAmount(
						this.convertUnitPrice(saleOrderLine.getProduct().getInAti(), saleOrderLine.getTaxLine(),
								(BigDecimal) discounts.get(PriceListConstants.LINE_DISCOUNT_AMOUNT)));
			} else {
				saleOrderLine.setDiscountAmount((BigDecimal) discounts.get(PriceListConstants.LINE_DISCOUNT_AMOUNT));
			}
			saleOrderLine.setDiscountTypeSelect((Integer) discounts.get(PriceListConstants.LINE_DISCOUNT_TYPE_SELECT));
		} else if (!saleOrder.getTemplate()) {
			saleOrderLine.setDiscountAmount(BigDecimal.ZERO);
			saleOrderLine.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
		}

		return price;
	}
}
