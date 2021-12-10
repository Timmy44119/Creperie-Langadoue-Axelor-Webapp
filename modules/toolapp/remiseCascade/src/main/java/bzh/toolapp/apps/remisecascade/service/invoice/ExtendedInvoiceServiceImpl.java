package bzh.toolapp.apps.remisecascade.service.invoice;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.app.AppAccountService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.factory.CancelFactory;
import com.axelor.apps.account.service.invoice.factory.ValidateFactory;
import com.axelor.apps.account.service.invoice.factory.VentilateFactory;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.account.service.move.MoveToolService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.alarm.AlarmEngineService;
import com.axelor.apps.cash.management.service.InvoiceServiceManagementImpl;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.Inject;

public class ExtendedInvoiceServiceImpl extends InvoiceServiceManagementImpl implements InvoiceService {
	private final Logger logger = LoggerFactory.getLogger(InvoiceService.class);

	private final PriceListService priceListService;
	protected InvoiceService invoiceService;

	@Inject
	public ExtendedInvoiceServiceImpl(final ValidateFactory validateFactory, final VentilateFactory ventilateFactory,
			final CancelFactory cancelFactory, final AlarmEngineService<Invoice> alarmEngineService,
			final InvoiceRepository invoiceRepo, final AppAccountService appAccountService,
			final PartnerService partnerService, final InvoiceLineService invoiceLineService,
			final AccountConfigService accountConfigService, final MoveToolService moveToolService,
			final PriceListService priceListServiceParam, final InvoiceService invoiceServiceParam) {
		super(validateFactory, ventilateFactory, cancelFactory, alarmEngineService, invoiceRepo, appAccountService,
				partnerService, invoiceLineService, accountConfigService, moveToolService);
		this.priceListService = priceListServiceParam;
		this.invoiceService = invoiceServiceParam;
	}

	/**
	 * Fonction permettant de calculer l'intégralité d'une facture :
	 *
	 * <ul>
	 * <li>Détermine les taxes;
	 * <li>Détermine la TVA;
	 * <li>Détermine les totaux.
	 * </ul>
	 *
	 * (Transaction)
	 *
	 * @param invoice Une facture.
	 * @throws AxelorException
	 */
	@Override
	public Invoice compute(final Invoice invoice) throws AxelorException {

		final InvoiceGenerator invoiceGenerator = new ExtendedInvoiceGeneratorFromScratch(invoice,
				this.priceListService, this.invoiceService, this.invoiceLineService);

		final Invoice invoice1 = invoiceGenerator.generate();
		invoice1.setAdvancePaymentInvoiceSet(this.getDefaultAdvancePaymentInvoice(invoice1));

		return invoice1;
	}

	public void updateInvoiceLineList(final Invoice invoice) throws AxelorException {
		final List<InvoiceLine> invoiceLineList = invoice.getInvoiceLineList();
		if (invoiceLineList != null) {
			for (final InvoiceLine invoiceLine : invoiceLineList) {
				Beans.get(ExtendedInvoiceLineServiceImpl.class).computeValues(invoice, invoiceLine);
			}
		}

	}
}
