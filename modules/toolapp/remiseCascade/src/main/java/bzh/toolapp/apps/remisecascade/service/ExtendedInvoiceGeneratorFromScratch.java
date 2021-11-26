package bzh.toolapp.apps.remisecascade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.InvoiceLineTax;
import com.axelor.apps.account.db.PaymentCondition;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.BankDetails;
import com.axelor.apps.base.db.Company;
import com.axelor.apps.base.db.Currency;
import com.axelor.apps.base.db.Partner;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.TradingName;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

/** To generate Invoice for delivery from scratch. */
public class ExtendedInvoiceGeneratorFromScratch extends InvoiceGenerator {
	private final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

	private final Invoice invoice;
	private final PriceListService priceListService;
	protected int operationType;
	protected Company company;
	protected PaymentCondition paymentCondition;
	protected PaymentMode paymentMode;
	protected Address mainInvoicingAddress;
	protected Partner partner;
	protected Partner contactPartner;
	protected Currency currency;
	protected LocalDate today;
	protected PriceList priceList;
	protected String internalReference;
	protected String externalReference;
	protected Boolean inAti;
	protected BankDetails companyBankDetails;
	protected TradingName tradingName;
	protected static int DEFAULT_INVOICE_COPY = 1;
	protected Boolean header = false;
	protected PriceListService priceListServiceParam;

	@Inject
	protected AppBaseService appBaseService;

	public ExtendedInvoiceGeneratorFromScratch(final int operationType, final Company company,
			final PaymentCondition paymentCondition, final PaymentMode paymentMode, final Address mainInvoicingAddress,
			final Partner partner, final Partner contactPartner, final Currency currency, final PriceList priceList,
			final String internalReference, final String externalReference, final Boolean inAti,
			final BankDetails companyBankDetails, final TradingName tradingName, final boolean header,
			final PriceListService priceListServiceParam) throws AxelorException {
		super(operationType, company, paymentCondition, paymentMode, mainInvoicingAddress, partner, contactPartner,
				currency, priceList, internalReference, externalReference, inAti, companyBankDetails, tradingName);
		this.operationType = operationType;
		this.company = company;
		this.paymentCondition = paymentCondition;
		this.paymentMode = paymentMode;
		this.mainInvoicingAddress = mainInvoicingAddress;
		this.partner = partner;
		this.contactPartner = contactPartner;
		this.currency = currency;
		this.priceList = priceList;
		this.internalReference = internalReference;
		this.externalReference = externalReference;
		this.inAti = inAti;
		this.companyBankDetails = companyBankDetails;
		this.tradingName = tradingName;
		this.today = Beans.get(AppAccountService.class).getTodayDate(company);
		this.header = header;
		this.invoice = null;
		this.priceListService = priceListServiceParam;
	}

	public ExtendedInvoiceGeneratorFromScratch(final Invoice invoiceParam, final PriceListService priceListServiceParam)
			throws AxelorException {
		this.invoice = invoiceParam;
		this.priceListService = priceListServiceParam;
		this.header = false;
	}

	@Override
	public Invoice generate() throws AxelorException {
		Invoice invoiceGenerated;
		if (this.header) {
			invoiceGenerated = super.createInvoiceHeader();
			invoiceGenerated.setPartnerTaxNbr(this.partner.getTaxNbr());
			if (this.priceList != null) {
				invoiceGenerated.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
				invoiceGenerated.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
				invoiceGenerated.setDiscountAmount(this.priceList.getGeneralDiscount());
				invoiceGenerated.setSecDiscountAmount(this.priceList.getSecGeneralDiscount());
			} else {
				invoiceGenerated.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
				invoiceGenerated.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
				invoiceGenerated.setDiscountAmount(BigDecimal.ZERO);
				invoiceGenerated.setSecDiscountAmount(BigDecimal.ZERO);

			}
			this.logger.debug("{}", invoiceGenerated);
		} else {

			final List<InvoiceLine> invoiceLines = new ArrayList<>();
			if (this.invoice.getInvoiceLineList() != null) {
				invoiceLines.addAll(this.invoice.getInvoiceLineList());

				this.populate(this.invoice, invoiceLines);
			}
			invoiceGenerated = this.invoice;
		}

		return invoiceGenerated;
	}

	/**
	 * Compute the invoice total amounts
	 *
	 * @param invoice
	 * @throws AxelorException
	 */
	@Override
	public void computeInvoice(final Invoice invoice) throws AxelorException {

		// In the invoice currency
		invoice.setExTaxTotal(BigDecimal.ZERO);
		invoice.setTaxTotal(BigDecimal.ZERO);
		invoice.setInTaxTotal(BigDecimal.ZERO);

		// In the company accounting currency
		invoice.setCompanyExTaxTotal(BigDecimal.ZERO);
		invoice.setCompanyTaxTotal(BigDecimal.ZERO);
		invoice.setCompanyInTaxTotal(BigDecimal.ZERO);

		for (final InvoiceLine invoiceLine : invoice.getInvoiceLineList()) {
			// In the company accounting currency
			// TODO this computation should be updated too ...
			invoice.setCompanyExTaxTotal(invoice.getCompanyExTaxTotal().add(invoiceLine.getCompanyExTaxTotal()));

			// start computation of target price for each line using global discount
			// information
			final BigDecimal lineExTaxTotal = invoiceLine.getExTaxTotal();
			this.logger.debug("Prix HT {} de la ligne.", lineExTaxTotal);

			final BigDecimal intermediateExTaxPrice = this.computeGlobalDiscountPerLine(lineExTaxTotal, invoice)
					.setScale(2, RoundingMode.HALF_UP);

			// update global total without taxes
			invoice.setExTaxTotal(invoice.getExTaxTotal().add(intermediateExTaxPrice));
			final BigDecimal taxLineValue = invoiceLine.getTaxLine().getValue();
			final BigDecimal taxPrice = intermediateExTaxPrice.multiply(taxLineValue).setScale(2, RoundingMode.HALF_UP);

			// update also final total of taxes
			invoice.setTaxTotal(invoice.getTaxTotal().add(taxPrice).setScale(2, RoundingMode.HALF_UP));
			this.logger.debug("montant de la taxe {}", taxPrice);

			// compute price for this line with global discount (HT + taxes)
			final BigDecimal intermediateInTaxPrice = intermediateExTaxPrice.add(taxPrice);
			this.logger.debug("Remise globale appliquée sur le montant de la ligne : HT = {}, TTC = {}",
					intermediateExTaxPrice, intermediateInTaxPrice);

			// update also final total with taxes
			invoice.setInTaxTotal(
					invoice.getInTaxTotal().add(intermediateInTaxPrice).setScale(2, RoundingMode.HALF_UP));
			this.logger.debug("prix global intermédiaire : TTC = {}", invoice.getInTaxTotal());
		}

		for (final InvoiceLineTax invoiceLineTax : invoice.getInvoiceLineTaxList()) {
			// In the company accounting currency
			// TODO this computation should be updated too ...
			invoice.setCompanyTaxTotal(invoice.getCompanyTaxTotal()
					.add(invoiceLineTax.getCompanyTaxTotal().setScale(2, RoundingMode.HALF_UP)));
		}

		// In the company accounting currency
		// TODO this computation should be updated too ...
		invoice.setCompanyInTaxTotal(
				invoice.getCompanyExTaxTotal().add(invoice.getCompanyTaxTotal().setScale(2, RoundingMode.HALF_UP)));

		invoice.setAmountRemaining(invoice.getInTaxTotal());
		invoice.setHasPendingPayments(false);

		this.logger.debug("Invoice amounts : W.T. = {}, Tax = {}, A.T.I. = {}", invoice.getExTaxTotal(),
				invoice.getTaxTotal(), invoice.getInTaxTotal());
	}

	private BigDecimal computeGlobalDiscountPerLine(final BigDecimal originalPrice, final Invoice invoice) {
		/*
		 * Now, we have to use discount information to update amount without taxes, then
		 * compute again final amount with taxes.
		 */
		this.logger.debug("{}, {}, {}", originalPrice, invoice.getDiscountTypeSelect(), invoice.getDiscountAmount());
		// compute first discount
		final BigDecimal firstDiscount = this.priceListService.computeDiscount(originalPrice,
				invoice.getDiscountTypeSelect(), invoice.getDiscountAmount());
		// then second discount
		final BigDecimal secondDiscount = this.priceListService.computeDiscount(firstDiscount,
				invoice.getSecDiscountTypeSelect(), invoice.getSecDiscountAmount());
		return secondDiscount;
	}
}
