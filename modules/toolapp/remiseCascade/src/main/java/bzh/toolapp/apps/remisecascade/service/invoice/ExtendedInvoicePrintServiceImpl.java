package bzh.toolapp.apps.remisecascade.service.invoice;

import com.axelor.apps.ReportFactory;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.repo.AccountConfigRepository;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.exception.IExceptionMessage;
import com.axelor.apps.account.service.invoice.InvoiceToolService;
import com.axelor.apps.account.service.invoice.print.InvoicePrintServiceImpl;
import com.axelor.apps.report.engine.ReportSettings;
import com.axelor.apps.stock.db.repo.StockMoveLineRepository;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.repo.TraceBackRepository;
import com.axelor.i18n.I18n;
import com.axelor.meta.MetaFiles;
import com.google.common.base.Strings;
import java.util.Optional;
import javax.inject.Inject;

public class ExtendedInvoicePrintServiceImpl extends InvoicePrintServiceImpl {

  final StockMoveLineRepository stockMoveLineRepo;

  @Inject
  public ExtendedInvoicePrintServiceImpl(
      final InvoiceRepository invoiceRepo,
      final AccountConfigRepository accountConfigRepo,
      final StockMoveLineRepository stockMoveLineRepoParam) {
    super(invoiceRepo, accountConfigRepo);
    this.stockMoveLineRepo = stockMoveLineRepoParam;
  }

  @Override
  public ReportSettings prepareReportSettings(
      final Invoice invoice, final Integer reportType, final String format, String locale)
      throws AxelorException {
    if (invoice.getPrintingSettings() == null) {
      throw new AxelorException(
          TraceBackRepository.CATEGORY_MISSING_FIELD,
          String.format(
              I18n.get(IExceptionMessage.INVOICE_MISSING_PRINTING_SETTINGS),
              invoice.getInvoiceId()),
          invoice);
    }

    String title = I18n.get(InvoiceToolService.isRefund(invoice) ? "Refund" : "Invoice");
    if (invoice.getInvoiceId() != null) {
      title += " " + invoice.getInvoiceId();
    }

    final ReportSettings reportSetting =
        ReportFactory.createReport("CreperieReport.rptdesign", title + " - ${date}");

    if (Strings.isNullOrEmpty(locale)) {
      final String userLanguageCode =
          Optional.ofNullable(AuthUtils.getUser()).map(User::getLanguage).orElse(null);
      final String companyLanguageCode =
          invoice.getCompany().getLanguage() != null
              ? invoice.getCompany().getLanguage().getCode()
              : userLanguageCode;
      final String partnerLanguageCode =
          invoice.getPartner().getLanguage() != null
              ? invoice.getPartner().getLanguage().getCode()
              : userLanguageCode;
      locale =
          this.accountConfigRepo
                  .findByCompany(invoice.getCompany())
                  .getIsPrintInvoicesInCompanyLanguage()
              ? companyLanguageCode
              : partnerLanguageCode;
    }
    String watermark = null;
    if (this.accountConfigRepo.findByCompany(invoice.getCompany()).getInvoiceWatermark() != null) {
      watermark =
          MetaFiles.getPath(
                  this.accountConfigRepo.findByCompany(invoice.getCompany()).getInvoiceWatermark())
              .toString();
    }

    return reportSetting
        .addParam("InvoiceId", invoice.getId().intValue())
        .addParam("Locale", locale)
        .addParam(
            "Timezone", invoice.getCompany() != null ? invoice.getCompany().getTimezone() : null)
        .addParam("ReportType", reportType == null ? 0 : reportType)
        .addParam("HeaderHeight", invoice.getPrintingSettings().getPdfHeaderHeight())
        .addParam("Watermark", watermark)
        .addParam("FooterHeight", invoice.getPrintingSettings().getPdfFooterHeight())
        .addFormat(format);
  }
}
