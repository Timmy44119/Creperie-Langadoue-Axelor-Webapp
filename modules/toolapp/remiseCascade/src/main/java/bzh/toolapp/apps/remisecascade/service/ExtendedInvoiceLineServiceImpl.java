package bzh.toolapp.apps.remisecascade.service;

import java.math.BigDecimal;

import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.service.AccountManagementAccountService;
import com.axelor.apps.account.service.AnalyticMoveLineService;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.base.service.CurrencyService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.ProductCompanyService;
import com.axelor.apps.businessproject.service.InvoiceLineProjectServiceImpl;
import com.axelor.apps.purchase.service.PurchaseProductService;
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
}
