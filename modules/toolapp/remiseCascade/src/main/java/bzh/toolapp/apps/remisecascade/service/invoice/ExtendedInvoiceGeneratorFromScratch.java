package bzh.toolapp.apps.remisecascade.service.invoice;

import java.math.BigDecimal;
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
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.invoice.generator.line.InvoiceLineManagement;
import com.axelor.apps.account.service.invoice.generator.tax.TaxInvoiceLine;
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
	protected InvoiceLineService invoiceLineService;
	protected InvoiceService invoiceService;

	@Inject
	protected AppBaseService appBaseService;

	@Inject
	protected TaxInvoiceLine taxInvoiceLine;

	public ExtendedInvoiceGeneratorFromScratch(final int operationType, final Company company,
			final PaymentCondition paymentCondition, final PaymentMode paymentMode, final Address mainInvoicingAddress,
			final Partner partner, final Partner contactPartner, final Currency currency, final PriceList priceList,
			final String internalReference, final String externalReference, final Boolean inAti,
			final BankDetails companyBankDetails, final TradingName tradingName, final boolean header,
			final PriceListService priceListServiceParam, final InvoiceLineService invoiceLineServiceParam,
			final InvoiceService invoiceServiceParam) throws AxelorException {
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
		this.invoiceLineService = invoiceLineServiceParam;
		this.invoiceService = invoiceServiceParam;
	}

	public ExtendedInvoiceGeneratorFromScratch(final Invoice invoiceParam, final PriceListService priceListServiceParam,
			final InvoiceService invoiceServiceParam, final InvoiceLineService invoiceLineServiceParam)
			throws AxelorException {
		this.invoice = invoiceParam;
		this.priceListService = priceListServiceParam;
		this.header = false;
		this.invoiceLineService = invoiceLineServiceParam;
		this.invoiceService = invoiceServiceParam;
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

		List<InvoiceLine> invoiceLineList = invoice.getInvoiceLineList();
		// Application de la remise sur les lignes de la facture
		invoiceLineList = this.computeValueLines(invoice, invoiceLineList);

		for (final InvoiceLine invoiceLine : invoiceLineList) {
			// In the invoice currency
			invoice.setExTaxTotal(
					invoice.getExTaxTotal().add(invoiceLine.getExTaxTotal().setScale(2, BigDecimal.ROUND_HALF_UP)));

			// In the company accounting currency
			invoice.setCompanyExTaxTotal(invoice.getCompanyExTaxTotal()
					.add(invoiceLine.getCompanyExTaxTotal().setScale(2, BigDecimal.ROUND_HALF_UP)));
		}

		// MAJ des lignes dans la facture
		invoice.setInvoiceLineList(invoiceLineList);

		this.logger.debug("Taxe list {}", invoice.getInvoiceLineTaxList());

		// Lancement du recalcul des taxes
		final TaxInvoiceLine taxInvoiceLine = new TaxInvoiceLine(invoice, invoiceLineList);
		final List<InvoiceLineTax> invoiceLineTaxList = taxInvoiceLine.creates();

		// MAJ des taxes
		this.logger.debug("Taxe list {}", invoiceLineTaxList);

		invoice.getInvoiceLineTaxList().clear();
		invoice.getInvoiceLineTaxList().addAll(invoiceLineTaxList);

		for (final InvoiceLineTax invoiceLineTax : invoice.getInvoiceLineTaxList()) {

			// In the invoice currency
			invoice.setTaxTotal(invoice.getTaxTotal().add(invoiceLineTax.getTaxTotal()));

			// In the company accounting currency
			invoice.setCompanyTaxTotal(invoice.getCompanyTaxTotal().add(invoiceLineTax.getCompanyTaxTotal()));
		}

		// In the invoice currency
		invoice.setInTaxTotal(invoice.getExTaxTotal().add(invoice.getTaxTotal()));

		// In the company accounting currency
		invoice.setCompanyInTaxTotal(invoice.getCompanyExTaxTotal().add(invoice.getCompanyTaxTotal()));

		invoice.setAmountRemaining(invoice.getInTaxTotal());
		invoice.setHasPendingPayments(false);
	}

	private List<InvoiceLine> computeValueLines(final Invoice invoice, final List<InvoiceLine> invoiceLineList)
			throws AxelorException {

		for (InvoiceLine invoiceLine : invoiceLineList) {
			invoiceLine = this.updateInvoiceLine(invoice, invoiceLine);
		}

		return invoiceLineList;
	}

	// Mise à jour des montants de chaque ligne de la facture
	private InvoiceLine updateInvoiceLine(final Invoice invoice, final InvoiceLine invoiceLine) throws AxelorException {

		BigDecimal exTaxTotal;
		final BigDecimal companyExTaxTotal;
		BigDecimal inTaxTotal;
		final BigDecimal companyInTaxTotal;
		final BigDecimal priceDiscounted = this.computeDiscount(invoiceLine, invoice.getInAti());

		BigDecimal taxRate = BigDecimal.ZERO;
		if (invoiceLine.getTaxLine() != null) {
			taxRate = invoiceLine.getTaxLine().getValue();
		}

		// Calcul des montants globaux
		if (!invoice.getInAti()) {
			exTaxTotal = InvoiceLineManagement.computeAmount(invoiceLine.getQty(), priceDiscounted).setScale(2,
					BigDecimal.ROUND_HALF_UP);
			// Application des remises globales
			exTaxTotal = this.computeGlobalDiscount(invoice, exTaxTotal).setScale(2, BigDecimal.ROUND_HALF_UP);
			inTaxTotal = exTaxTotal.add(exTaxTotal.multiply(taxRate));
		} else {
			inTaxTotal = InvoiceLineManagement.computeAmount(invoiceLine.getQty(), priceDiscounted).setScale(2,
					BigDecimal.ROUND_HALF_UP);
			// Application des remises globales
			inTaxTotal = this.computeGlobalDiscount(invoice, inTaxTotal).setScale(2, BigDecimal.ROUND_HALF_UP);
			exTaxTotal = inTaxTotal.divide(taxRate.add(BigDecimal.ONE), 2, BigDecimal.ROUND_HALF_UP);
		}

		companyExTaxTotal = this.invoiceLineService.getCompanyExTaxTotal(exTaxTotal, invoice);
		companyInTaxTotal = this.invoiceLineService.getCompanyExTaxTotal(inTaxTotal, invoice);

		// MAJ de la ligne de la facture
		invoiceLine.setPriceDiscounted(priceDiscounted);
		invoiceLine.setExTaxTotal(exTaxTotal);
		invoiceLine.setInTaxPrice(inTaxTotal);
		invoiceLine.setCompanyExTaxTotal(companyExTaxTotal);
		invoiceLine.setCompanyInTaxTotal(companyInTaxTotal);

		return invoiceLine;
	}

	// Application des remises globales
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

	public BigDecimal computeDiscount(final InvoiceLine invoiceLine, final Boolean inAti) {

		final BigDecimal unitPrice = inAti ? invoiceLine.getInTaxPrice() : invoiceLine.getPrice();

		if (invoiceLine.getProduct().getIsShippingCostsProduct()) {
			return unitPrice;
		}
		// compute first discount
		final BigDecimal firstDiscount = this.priceListService.computeDiscount(unitPrice,
				invoiceLine.getDiscountTypeSelect(), invoiceLine.getDiscountAmount());
		// then second discount
		final BigDecimal secondDiscount = this.priceListService.computeDiscount(firstDiscount,
				invoiceLine.getSecDiscountTypeSelect(), invoiceLine.getSecDiscountAmount());
		return secondDiscount;
	}
}
