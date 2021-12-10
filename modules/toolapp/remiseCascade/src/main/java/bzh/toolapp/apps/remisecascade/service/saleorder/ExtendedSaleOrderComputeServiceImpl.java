package bzh.toolapp.apps.remisecascade.service.saleorder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.SaleOrderLine;
import com.axelor.apps.sale.db.SaleOrderLineTax;
import com.axelor.apps.sale.db.repo.SaleOrderLineRepository;
import com.axelor.apps.sale.service.saleorder.SaleOrderComputeService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineService;
import com.axelor.apps.sale.service.saleorder.SaleOrderLineTaxService;
import com.axelor.apps.supplychain.service.SaleOrderComputeServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.SaleOrderServiceSupplychainImpl;
import com.axelor.apps.supplychain.service.app.AppSupplychainService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class ExtendedSaleOrderComputeServiceImpl extends SaleOrderComputeServiceSupplychainImpl
		implements SaleOrderComputeService {

	private final Logger logger = LoggerFactory.getLogger(SaleOrderComputeService.class);

	protected PriceListService priceListService;
	protected ExtendedSaleOrderLineServiceImpl extendedSaleOrderLineServiceImpl;
	@Inject
	protected AppBaseService appBaseService;

	@Inject
	protected ProductCompanyService productCompanyService;

	@Inject
	public ExtendedSaleOrderComputeServiceImpl(final SaleOrderLineService saleOrderLineService,
			final SaleOrderLineTaxService saleOrderLineTaxService, final PriceListService priceListServiceParam,
			final ExtendedSaleOrderLineServiceImpl extendedSaleOrderLineServiceImplParam) {

		super(saleOrderLineService, saleOrderLineTaxService);
		this.priceListService = priceListServiceParam;
		this.extendedSaleOrderLineServiceImpl = extendedSaleOrderLineServiceImplParam;
	}

	@Override
	public void _computeSaleOrder(final SaleOrder saleOrder) throws AxelorException {
		saleOrder.setExTaxTotal(BigDecimal.ZERO);
		saleOrder.setCompanyExTaxTotal(BigDecimal.ZERO);
		saleOrder.setTaxTotal(BigDecimal.ZERO);
		saleOrder.setInTaxTotal(BigDecimal.ZERO);
		/*
		 * for (final SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {
		 *
		 * // skip title lines in computing total amounts if
		 * (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_TITLE) {
		 * continue; } final BigDecimal lineExTaxTotal = saleOrderLine.getExTaxTotal();
		 * this.logger.debug("Prix HT {} de la ligne.", lineExTaxTotal);
		 *
		 * // In the company accounting currency // TODO this computation should be
		 * updated too ...
		 * saleOrder.setCompanyExTaxTotal(saleOrder.getCompanyExTaxTotal().add(
		 * saleOrderLine.getCompanyExTaxTotal()));
		 *
		 * final BigDecimal intermediateExTaxPrice =
		 * this.computeGlobalDiscountPerLine(lineExTaxTotal, saleOrder);
		 *
		 * // update global total without taxes
		 * saleOrder.setExTaxTotal(saleOrder.getExTaxTotal().add(intermediateExTaxPrice)
		 * ); final BigDecimal taxLineValue = saleOrderLine.getTaxLine().getValue();
		 * final BigDecimal taxPrice = intermediateExTaxPrice.multiply(taxLineValue);
		 *
		 * // update also final total of taxes
		 * saleOrder.setTaxTotal(saleOrder.getTaxTotal().add(taxPrice).setScale(2,
		 * RoundingMode.HALF_UP)); this.logger.debug("montant de la taxe {}", taxPrice);
		 *
		 * // compute price for this line with global discount (HT + taxes) final
		 * BigDecimal intermediateInTaxPrice = intermediateExTaxPrice.add(taxPrice);
		 * this.logger.
		 * debug("Remise globale appliquÃ©e sur le montant de la ligne : HT = {}, TTC = {}"
		 * , intermediateExTaxPrice, intermediateInTaxPrice);
		 *
		 * // update also final total with taxes saleOrder.setInTaxTotal(
		 * saleOrder.getInTaxTotal().add(intermediateInTaxPrice).setScale(2,
		 * RoundingMode.HALF_UP));
		 * this.logger.debug("prix global intermÃ©diaire : TTC = {}",
		 * saleOrder.getInTaxTotal()); }
		 */
		List<SaleOrderLine> saleOrderLineList = saleOrder.getSaleOrderLineList();
		// Relance du montant ht de l'ensemble des lignes de la commande
		saleOrderLineList = this.computeValueLines(saleOrder, saleOrder.getSaleOrderLineList());

		for (final SaleOrderLine saleOrderLine : saleOrderLineList) {

			// skip title lines in computing total amounts
			if (saleOrderLine.getTypeSelect() == SaleOrderLineRepository.TYPE_TITLE) {
				continue;
			}
			saleOrder.setExTaxTotal(saleOrder.getExTaxTotal().add(saleOrderLine.getExTaxTotal()));

			// In the company accounting currency
			saleOrder.setCompanyExTaxTotal(saleOrder.getCompanyExTaxTotal().add(saleOrderLine.getCompanyExTaxTotal()));
		}

		this.initSaleOrderLineTaxList(saleOrder);

		this._populateSaleOrder(saleOrder);

		for (final SaleOrderLineTax saleOrderLineVat : saleOrder.getSaleOrderLineTaxList()) {

			// In the sale order currency
			saleOrder.setTaxTotal(saleOrder.getTaxTotal().add(saleOrderLineVat.getTaxTotal()));
		}

		saleOrder.setInTaxTotal(saleOrder.getExTaxTotal().add(saleOrder.getTaxTotal()));
		saleOrder.setAdvanceTotal(this.computeTotalAdvancePayment(saleOrder));
		this.logger.debug("Montant de la facture: HTT = {},  HT = {}, TTC = {}", saleOrder.getExTaxTotal(),
				saleOrder.getTaxTotal(), saleOrder.getInTaxTotal());

		// duplicate also supplychain behavior
		if (!Beans.get(AppSupplychainService.class).isApp("supplychain")) {
			return;
		}

		int maxDelay = 0;

		if ((saleOrder.getSaleOrderLineList() != null) && !saleOrder.getSaleOrderLineList().isEmpty()) {
			for (final SaleOrderLine saleOrderLine : saleOrder.getSaleOrderLineList()) {

				if (((saleOrderLine.getSaleSupplySelect() == SaleOrderLineRepository.SALE_SUPPLY_PRODUCE)
						|| (saleOrderLine.getSaleSupplySelect() == SaleOrderLineRepository.SALE_SUPPLY_PURCHASE))) {
					maxDelay = Integer.max(maxDelay,
							saleOrderLine.getStandardDelay() == null ? 0 : saleOrderLine.getStandardDelay());
				}
			}
		}
		saleOrder.setStandardDelay(maxDelay);

		if (Beans.get(AppAccountService.class).getAppAccount().getManageAdvancePaymentInvoice()) {
			saleOrder.setAdvanceTotal(this.computeTotalInvoiceAdvancePayment(saleOrder));
		}
		Beans.get(SaleOrderServiceSupplychainImpl.class).updateAmountToBeSpreadOverTheTimetable(saleOrder);

	}

	// Application des remises globales
	private List<SaleOrderLine> computeValueLines(final SaleOrder saleOrder,
			final List<SaleOrderLine> saleOrderLineList) throws AxelorException {

		for (SaleOrderLine saleOrderLine : saleOrderLineList) {
			saleOrderLine = this.updateSaleOrderLine(saleOrder, saleOrderLine);
		}

		return saleOrderLineList;
	}

	// Lancement du calcul des montants par lignes
	private SaleOrderLine updateSaleOrderLine(final SaleOrder saleOrder, final SaleOrderLine saleOrderLine)
			throws AxelorException {
		BigDecimal exTaxTotal;
		BigDecimal companyExTaxTotal;
		BigDecimal inTaxTotal;
		BigDecimal companyInTaxTotal;
		BigDecimal priceFinaleDiscounted;
		final BigDecimal priceSecDiscounted;
		final HashMap<String, BigDecimal> map = new HashMap<>();

		BigDecimal priceDiscounted;

		priceDiscounted = this.extendedSaleOrderLineServiceImpl.computeDiscount(saleOrderLine, saleOrder.getInAti());

		// Verification si une deuxième remise est applicable
		if (saleOrderLine.getSecDiscountTypeSelect() != PriceListLineRepository.AMOUNT_TYPE_NONE) {
			priceSecDiscounted = this.extendedSaleOrderLineServiceImpl.computeSecDiscount(saleOrderLine,
					priceDiscounted);
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
			exTaxTotal = this.saleOrderLineService.computeAmount(saleOrderLine.getQty(), priceFinaleDiscounted);
			// Calcul du montant total avec les remises globales
			exTaxTotal = this.extendedSaleOrderLineServiceImpl.computeGlobalDiscount(saleOrder, exTaxTotal);
			inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
			companyExTaxTotal = this.saleOrderLineService.getAmountInCompanyCurrency(exTaxTotal, saleOrder);
			companyInTaxTotal = companyExTaxTotal.add(companyExTaxTotal.multiply(taxRate));
		} else {
			// Calcul du montant total
			inTaxTotal = this.saleOrderLineService.computeAmount(saleOrderLine.getQty(), priceFinaleDiscounted);
			// Calcul du montant total avec les remises globales
			exTaxTotal = this.extendedSaleOrderLineServiceImpl.computeGlobalDiscount(saleOrder, inTaxTotal);
			exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
			companyInTaxTotal = this.saleOrderLineService.getAmountInCompanyCurrency(inTaxTotal, saleOrder);
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
		saleOrderLine.setPriceDiscounted(priceDiscounted);
		saleOrderLine.setPriceSecDiscounted(priceSecDiscounted);
		saleOrderLine.setCompanyInTaxTotal(companyInTaxTotal);
		saleOrderLine.setCompanyExTaxTotal(companyExTaxTotal);
		saleOrderLine.setSubTotalCostPrice(subTotalCostPrice);

		map.putAll(this.saleOrderLineService.computeSubMargin(saleOrder, saleOrderLine));
		return saleOrderLine;
	}

	private BigDecimal computeGlobalDiscountPerLine(final BigDecimal originalPrice, final SaleOrder saleOrder) {
		/*
		 * Now, we have to use discount information to update amount without taxes, then
		 * compute again final amount with taxes.
		 */
		// compute first discount
		final BigDecimal firstDiscount = this.priceListService.computeDiscount(originalPrice,
				saleOrder.getDiscountTypeSelect(), saleOrder.getDiscountAmount());

		// then second discount
		final BigDecimal secondDiscount = this.priceListService.computeDiscount(firstDiscount,
				saleOrder.getSecDiscountTypeSelect(), saleOrder.getSecDiscountAmount());
		return secondDiscount;
	}
}
