package bzh.toolapp.apps.remisecascade.service;

import java.math.BigDecimal;
import java.util.Map;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.businessproject.service.InvoiceLineProjectServiceImpl;
import com.axelor.apps.purchase.service.PurchaseProductService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;

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

		final BigDecimal unitPrice = inAti ? invoiceLine.getInTaxPrice() : invoiceLine.getPrice();

		// compute first discount
		final BigDecimal firstDiscount = this.priceListService.computeDiscount(unitPrice,
				invoiceLine.getDiscountTypeSelect(), invoiceLine.getDiscountAmount());
		// then second discount
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
}
