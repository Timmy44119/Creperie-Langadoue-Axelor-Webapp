package bzh.toolapp.apps.remisecascade.service.saleorder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.businessproduction.service.SaleOrderLineBusinessProductionServiceImpl;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.exception.AxelorException;

public class ExtendedSaleOrderLineServiceImpl extends SaleOrderLineBusinessProductionServiceImpl
		implements SaleOrderLineService {
	private final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

	/*
	 * @Override public BigDecimal computeDiscount(final SaleOrderLine
	 * saleOrderLine, final Boolean inAti) {
	 *
	 * final BigDecimal price = inAti ? saleOrderLine.getInTaxPrice() :
	 * saleOrderLine.getPrice();
	 *
	 * // compute first discount final BigDecimal firstDiscount =
	 * this.priceListService.computeDiscount(price,
	 * saleOrderLine.getDiscountTypeSelect(), saleOrderLine.getDiscountAmount());
	 * this.logger.debug("Le montant de la premiere remise est de {}",
	 * firstDiscount); // then second discount final BigDecimal secondDiscount =
	 * this.priceListService.computeDiscount(firstDiscount,
	 * saleOrderLine.getSecDiscountTypeSelect(),
	 * saleOrderLine.getSecDiscountAmount());
	 *
	 * return secondDiscount; }
	 */

	// Calcul de la seconde remises
	public BigDecimal computeSecDiscount(final SaleOrderLine saleOrderLine, final BigDecimal priceDiscounted) {

		return this.priceListService.computeDiscount(priceDiscounted, saleOrderLine.getSecDiscountTypeSelect(),
				saleOrderLine.getSecDiscountAmount());
	}

	@Override
	public Map<String, BigDecimal> computeValues(final SaleOrder saleOrder, final SaleOrderLine saleOrderLine)
			throws AxelorException {

		final HashMap<String, BigDecimal> map = new HashMap<>();
		if ((saleOrder == null) || (saleOrderLine.getPrice() == null) || (saleOrderLine.getInTaxPrice() == null)
				|| (saleOrderLine.getQty() == null)) {
			return map;
		}

		BigDecimal exTaxTotal;
		BigDecimal companyExTaxTotal;
		BigDecimal inTaxTotal;
		BigDecimal companyInTaxTotal;
		BigDecimal priceFinaleDiscounted;
		final BigDecimal priceSecDiscounted;

		final BigDecimal priceDiscounted = this.computeDiscount(saleOrderLine, saleOrder.getInAti());

		// Verification si une deuxième remise est applicable
		if (saleOrderLine.getSecDiscountTypeSelect() != PriceListLineRepository.AMOUNT_TYPE_NONE) {
			priceSecDiscounted = this.computeSecDiscount(saleOrderLine, priceDiscounted);
		} else {
			priceSecDiscounted = BigDecimal.ZERO;
		}
		// Définition de la remise finale
		if (priceSecDiscounted != BigDecimal.ZERO) {
			priceFinaleDiscounted = priceSecDiscounted;
		} else {
			priceFinaleDiscounted = priceDiscounted;
		}

		BigDecimal taxRate = BigDecimal.ZERO;
		BigDecimal subTotalCostPrice = BigDecimal.ZERO;

		// Récupération des montants des taxe
		if (saleOrderLine.getTaxLine() != null) {
			taxRate = saleOrderLine.getTaxLine().getValue();
		}

		// Calcul des montants
		if (!saleOrder.getInAti()) {
			// Calcul du montant total
			exTaxTotal = this.computeAmount(saleOrderLine.getQty(), priceFinaleDiscounted);
			// Calcul du montant total avec les remises globales
			exTaxTotal = this.computeGlobalDiscount(saleOrder, exTaxTotal);
			inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
			companyExTaxTotal = this.getAmountInCompanyCurrency(exTaxTotal, saleOrder);
			companyInTaxTotal = companyExTaxTotal.add(companyExTaxTotal.multiply(taxRate));
		} else {
			// Calcul du montant total
			inTaxTotal = this.computeAmount(saleOrderLine.getQty(), priceFinaleDiscounted);
			// Calcul du montant total avec les remises globales
			exTaxTotal = this.computeGlobalDiscount(saleOrder, inTaxTotal);
			exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
			companyInTaxTotal = this.getAmountInCompanyCurrency(inTaxTotal, saleOrder);
			companyExTaxTotal = companyInTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
		}

		// Calcul du sous-total
		if ((saleOrderLine.getProduct() != null)
				&& (((BigDecimal) this.productCompanyService.get(saleOrderLine.getProduct(), "costPrice",
						saleOrder.getCompany())).compareTo(BigDecimal.ZERO) != 0)) {
			subTotalCostPrice = ((BigDecimal) this.productCompanyService.get(saleOrderLine.getProduct(), "costPrice",
					saleOrder.getCompany())).multiply(saleOrderLine.getQty());
		}
		// Modification des zones
		saleOrderLine.setInTaxTotal(inTaxTotal);
		saleOrderLine.setExTaxTotal(exTaxTotal);
		saleOrderLine.setPriceDiscounted(priceFinaleDiscounted);
		saleOrderLine.setPriceSecDiscounted(priceSecDiscounted);
		saleOrderLine.setCompanyInTaxTotal(companyInTaxTotal);
		saleOrderLine.setCompanyExTaxTotal(companyExTaxTotal);
		saleOrderLine.setSubTotalCostPrice(subTotalCostPrice);

		// Ajout des zones
		map.put("inTaxTotal", inTaxTotal);
		map.put("exTaxTotal", exTaxTotal);
		map.put("priceDiscounted", priceFinaleDiscounted);
		map.put("priceSecDiscounted", priceSecDiscounted);
		map.put("companyExTaxTotal", companyExTaxTotal);
		map.put("companyInTaxTotal", companyInTaxTotal);
		map.put("subTotalCostPrice", subTotalCostPrice);

		map.putAll(this.computeSubMargin(saleOrder, saleOrderLine));

		return map;
	}

	protected BigDecimal computeGlobalDiscount(final SaleOrder saleOrder, BigDecimal totalAmount) {
		// Controle sur le type de la premiere remise globale
		if (saleOrder.getDiscountTypeSelect() != PriceListLineRepository.AMOUNT_TYPE_NONE) {
			totalAmount = this.priceListService.computeDiscount(totalAmount, saleOrder.getDiscountTypeSelect(),
					saleOrder.getDiscountAmount());
		}

		// Controle sur le type de la deuxieme remise globale
		if (saleOrder.getDiscountTypeSelect() != PriceListLineRepository.AMOUNT_TYPE_NONE) {
			totalAmount = this.priceListService.computeDiscount(totalAmount, saleOrder.getSecDiscountTypeSelect(),
					saleOrder.getSecDiscountAmount());
		}

		// Renvoi du montant globale remise
		return totalAmount;
	}

	/*
	 * @Override public Map<String, Object> getDiscountsFromPriceLists(final
	 * SaleOrder saleOrder, final SaleOrderLine saleOrderLine, final BigDecimal
	 * price) {
	 *
	 * Map<String, Object> discounts = null;
	 *
	 * final PriceList priceList = saleOrder.getPriceList();
	 *
	 * if (priceList != null) { final PriceListLine priceListLine =
	 * this.getPriceListLine(saleOrderLine, priceList, price); discounts =
	 * this.priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine,
	 * price);
	 *
	 * // disable manual replacements }
	 *
	 * return discounts; }
	 */

	/*
	 * @Override protected BigDecimal fillDiscount(final SaleOrderLine
	 * saleOrderLine, final SaleOrder saleOrder, BigDecimal price) { final
	 * Map<String, Object> discounts = this.getDiscountsFromPriceLists(saleOrder,
	 * saleOrderLine, price);
	 *
	 * if (discounts != null) { if (discounts.get("price") != null) { price =
	 * (BigDecimal) discounts.get("price"); } if
	 * ((saleOrderLine.getProduct().getInAti() != saleOrder.getInAti()) &&
	 * ((Integer) discounts.get( PriceListConstants.LINE_DISCOUNT_TYPE_SELECT) !=
	 * PriceListLineRepository.AMOUNT_TYPE_PERCENT)) {
	 * saleOrderLine.setDiscountAmount(
	 * this.convertUnitPrice(saleOrderLine.getProduct().getInAti(),
	 * saleOrderLine.getTaxLine(), (BigDecimal)
	 * discounts.get(PriceListConstants.LINE_DISCOUNT_AMOUNT))); } else {
	 * saleOrderLine.setDiscountAmount((BigDecimal)
	 * discounts.get(PriceListConstants.LINE_DISCOUNT_AMOUNT)); }
	 * saleOrderLine.setDiscountTypeSelect((Integer)
	 * discounts.get(PriceListConstants.LINE_DISCOUNT_TYPE_SELECT)); } else if
	 * (!saleOrder.getTemplate()) {
	 * saleOrderLine.setDiscountAmount(BigDecimal.ZERO);
	 * saleOrderLine.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE)
	 * ; }
	 *
	 * return price; }
	 */
}
