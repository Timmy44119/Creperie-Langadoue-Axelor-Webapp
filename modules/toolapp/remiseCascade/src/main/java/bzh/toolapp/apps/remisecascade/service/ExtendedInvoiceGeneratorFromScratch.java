package bzh.toolapp.apps.remisecascade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.InvoiceLineTax;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;

/** To generate Invoice for delivery from scratch. */
public class ExtendedInvoiceGeneratorFromScratch extends InvoiceGenerator {
	private final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

	private final Invoice invoice;
	private final PriceListService priceListService;
	@Inject
	protected AppBaseService appBaseService;

	public ExtendedInvoiceGeneratorFromScratch(final Invoice invoiceParam, final PriceListService priceListServiceParam)
			throws AxelorException {
		this.invoice = invoiceParam;
		this.priceListService = priceListServiceParam;
	}

	@Override
	public Invoice generate() throws AxelorException {

		final List<InvoiceLine> invoiceLines = new ArrayList<>();
		invoiceLines.addAll(this.invoice.getInvoiceLineList());

		this.populate(this.invoice, invoiceLines);

		return this.invoice;
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
		// compute first discount
		final BigDecimal firstDiscount = this.priceListService.computeDiscount(originalPrice,
				invoice.getDiscountTypeSelect(), invoice.getDiscountAmount());
		// then second discount
		final BigDecimal secondDiscount = this.priceListService.computeDiscount(firstDiscount,
				invoice.getSecDiscountTypeSelect(), invoice.getSecDiscountAmount());
		return secondDiscount;
	}
}
