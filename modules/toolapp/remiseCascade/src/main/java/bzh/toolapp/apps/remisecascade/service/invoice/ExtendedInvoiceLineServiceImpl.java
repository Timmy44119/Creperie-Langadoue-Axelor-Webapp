package bzh.toolapp.apps.remisecascade.service.invoice;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.line.InvoiceLineManagement;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.businessproject.service.InvoiceLineProjectServiceImpl;
import com.axelor.apps.purchase.service.PurchaseProductService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

import bzh.toolapp.apps.remisecascade.service.pricelist.PriceListConstants;

public class ExtendedInvoiceLineServiceImpl extends InvoiceLineProjectServiceImpl implements InvoiceLineService {

	@Inject
	public ExtendedInvoiceLineServiceImpl(final CurrencyService currencyService,
			final PriceListService priceListService, final AppAccountService appAccountService,
			final AnalyticMoveLineService analyticMoveLineService,
			final AccountManagementAccountService accountManagementAccountService,
			final PurchaseProductService purchaseProductService, final ProductCompanyService productCompanyService) {
		super(currencyService, priceListService, appAccountService, analyticMoveLineService,
				accountManagementAccountService, purchaseProductService, productCompanyService);
	}

	@Override
	public BigDecimal computeDiscount(final InvoiceLine invoiceLine, final Boolean inAti) {

		final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

		final BigDecimal unitPrice = inAti ? invoiceLine.getInTaxPrice() : invoiceLine.getPrice();

		// Controle sur le type du produit
		if (invoiceLine.getProduct().getIsShippingCostsProduct()) {
			return unitPrice;
		}

		// Application de la premiere remise
		final BigDecimal firstDiscount = this.priceListService.computeDiscount(unitPrice,
				invoiceLine.getDiscountTypeSelect(), invoiceLine.getDiscountAmount());

		// Application de la seconde remise
		final BigDecimal secondDiscount = this.priceListService.computeDiscount(firstDiscount,
				invoiceLine.getSecDiscountTypeSelect(), invoiceLine.getSecDiscountAmount());

		return secondDiscount;
	}

	@Override
	public Map<String, Object> getDiscountsFromPriceLists(final Invoice invoice, final InvoiceLine invoiceLine,
			final BigDecimal price) {

		Map<String, Object> discounts = null;

		final PriceList priceList = invoice.getPriceList();

		if (priceList != null) {
			final PriceListLine priceListLine = this.getPriceListLine(invoiceLine, priceList, price);
			discounts = this.priceListService.getReplacedPriceAndDiscounts(priceList, priceListLine, price);
			// and disable totally old behavior (remove deprecated entries)
			discounts.put("discountAmount", BigDecimal.ZERO);
			discounts.put("discountTypeSelect", PriceListLineRepository.AMOUNT_TYPE_NONE);
		}

		return discounts;
	}

	@Override
	public Map<String, Object> getDiscount(final Invoice invoice, final InvoiceLine invoiceLine, final BigDecimal price)
			throws AxelorException {
		final Map<String, Object> processedDiscounts = super.getDiscount(invoice, invoiceLine, price);

		// add behavior to manage activation of discounts from price list
		final Map<String, Object> rawDiscounts = this.getDiscountsFromPriceLists(invoice, invoiceLine, price);
		if (rawDiscounts != null) {
			processedDiscounts.put("discountTypeSelect",
					rawDiscounts.get(PriceListConstants.LINE_DISCOUNT_TYPE_SELECT));
			processedDiscounts.put("discountAmount", rawDiscounts.get(PriceListConstants.LINE_DISCOUNT_AMOUNT));
			processedDiscounts.put("secDiscountTypeSelect",
					rawDiscounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_TYPE_SELECT));
			processedDiscounts.put("secDiscountAmount",
					rawDiscounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_AMOUNT));
		}

		return processedDiscounts;
	}

	public HashMap<String, BigDecimal> computeValues(final Invoice invoice, final InvoiceLine invoiceLine)
			throws AxelorException {

		final HashMap<String, BigDecimal> map = new HashMap<>();
		BigDecimal exTaxTotal;
		BigDecimal companyExTaxTotal;
		BigDecimal inTaxTotal;
		BigDecimal companyInTaxTotal;
		final BigDecimal priceDiscounted = this.computeDiscount(invoiceLine, invoice.getInAti());

		map.put("priceDiscounted", priceDiscounted);

		BigDecimal taxRate = BigDecimal.ZERO;
		if (invoiceLine.getTaxLine() != null) {
			taxRate = invoiceLine.getTaxLine().getValue();
			map.put("taxRate", taxRate);
		}

		if (!invoice.getInAti()) {
			exTaxTotal = InvoiceLineManagement.computeAmount(invoiceLine.getQty(), priceDiscounted);
			if (!invoiceLine.getProduct().getIsShippingCostsProduct()) {
				// Application des remises globales
				exTaxTotal = this.computeGlobalDiscount(invoice, exTaxTotal);
			}
			inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
		} else {
			inTaxTotal = InvoiceLineManagement.computeAmount(invoiceLine.getQty(), priceDiscounted);
			if (!invoiceLine.getProduct().getIsShippingCostsProduct()) {
				// Application des remises globales
				inTaxTotal = this.computeGlobalDiscount(invoice, inTaxTotal);
			}
			exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
		}

		companyExTaxTotal = this.getCompanyExTaxTotal(exTaxTotal, invoice);
		companyInTaxTotal = this.getCompanyExTaxTotal(inTaxTotal, invoice);

		map.put("exTaxTotal", exTaxTotal);
		map.put("inTaxTotal", inTaxTotal);
		map.put("companyInTaxTotal", companyInTaxTotal);
		map.put("companyExTaxTotal", companyExTaxTotal);

		return map;
	}

	protected BigDecimal computeGlobalDiscount(final Invoice invoice, BigDecimal totalAmount) {

		final PriceListService priceListService = Beans.get(PriceListService.class);
		// Controle sur le type de la premiere remise globale
		if (invoice.getDiscountTypeSelect() != PriceListLineRepository.AMOUNT_TYPE_NONE) {
			totalAmount = priceListService.computeDiscount(totalAmount, invoice.getDiscountTypeSelect(),
					invoice.getDiscountAmount());
		}

		// Controle sur le type de la deuxieme remise globale
		if (invoice.getDiscountTypeSelect() != PriceListLineRepository.AMOUNT_TYPE_NONE) {
			totalAmount = priceListService.computeDiscount(totalAmount, invoice.getSecDiscountTypeSelect(),
					invoice.getSecDiscountAmount());
		}

		// Renvoi du montant globale remise
		return totalAmount;
	}
}
