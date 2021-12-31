package bzh.toolapp.apps.remisecascade.service.stockmove;

import bzh.toolapp.apps.remisecascade.service.invoice.ExtendedInvoiceGeneratorFromScratch;
import com.axelor.apps.account.db.Invoice;
import com.axelor.apps.account.db.InvoiceLine;
import com.axelor.apps.account.db.repo.InvoiceRepository;
import com.axelor.apps.account.service.invoice.InvoiceLineService;
import com.axelor.apps.account.service.invoice.InvoiceService;
import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.Address;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.service.AddressService;
import com.axelor.apps.base.service.PartnerService;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.purchase.db.repo.PurchaseOrderRepository;
import com.axelor.apps.sale.db.SaleOrder;
import com.axelor.apps.sale.db.repo.SaleOrderRepository;
import com.axelor.apps.stock.db.StockMove;
import com.axelor.apps.stock.db.StockMoveLine;
import com.axelor.apps.stock.db.repo.StockMoveRepository;
import com.axelor.apps.supplychain.service.StockMoveInvoiceService;
import com.axelor.apps.supplychain.service.StockMoveMultiInvoiceServiceImpl;
import com.axelor.exception.AxelorException;
import com.axelor.inject.Beans;
import com.google.inject.persist.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedStockMoveMultiInvoiceServiceImpl extends StockMoveMultiInvoiceServiceImpl {

  private final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

  protected InvoiceRepository invoiceRepository;
  protected SaleOrderRepository saleOrderRepository;
  protected PurchaseOrderRepository purchaseOrderRepository;
  protected StockMoveInvoiceService stockMoveInvoiceService;
  protected PriceListService priceListService;
  protected InvoiceLineService invoiceLineService;
  protected InvoiceService invoiceService;

  @Inject
  public ExtendedStockMoveMultiInvoiceServiceImpl(
      final InvoiceRepository invoiceRepository,
      final SaleOrderRepository saleOrderRepository,
      final PurchaseOrderRepository purchaseOrderRepository,
      final StockMoveInvoiceService stockMoveInvoiceService,
      final PriceListService priceListService,
      final InvoiceLineService invoiceLineServiceParam,
      final InvoiceService invoiceServiceParam) {
    super(invoiceRepository, saleOrderRepository, purchaseOrderRepository, stockMoveInvoiceService);
    this.invoiceRepository = invoiceRepository;
    this.saleOrderRepository = saleOrderRepository;
    this.purchaseOrderRepository = purchaseOrderRepository;
    this.stockMoveInvoiceService = stockMoveInvoiceService;
    this.priceListService = priceListService;
    this.invoiceLineService = invoiceLineServiceParam;
    this.invoiceService = invoiceServiceParam;
  }

  /**
   * Create a dummy invoice to hold fields used to generate the invoice which will be saved.
   *
   * @param stockMove an out stock move.
   * @return the created dummy invoice.
   */
  @Override
  protected Invoice createDummyOutInvoice(final StockMove stockMove) {
    final Invoice dummyInvoice = new Invoice();

    if ((stockMove.getOriginId() != null)
        && StockMoveRepository.ORIGIN_SALE_ORDER.equals(stockMove.getOriginTypeSelect())) {
      final SaleOrder saleOrder = this.saleOrderRepository.find(stockMove.getOriginId());
      dummyInvoice.setCurrency(saleOrder.getCurrency());
      dummyInvoice.setPartner(saleOrder.getClientPartner());
      dummyInvoice.setCompany(saleOrder.getCompany());
      dummyInvoice.setTradingName(saleOrder.getTradingName());
      dummyInvoice.setPaymentCondition(saleOrder.getPaymentCondition());
      dummyInvoice.setPaymentMode(saleOrder.getPaymentMode());
      dummyInvoice.setAddress(saleOrder.getMainInvoicingAddress());
      dummyInvoice.setAddressStr(saleOrder.getMainInvoicingAddressStr());
      dummyInvoice.setContactPartner(saleOrder.getContactPartner());
      dummyInvoice.setPriceList(saleOrder.getPriceList());
      dummyInvoice.setInAti(saleOrder.getInAti());
      dummyInvoice.setDiscountTypeSelect(saleOrder.getDiscountTypeSelect());
      dummyInvoice.setDiscountAmount(saleOrder.getDiscountAmount());
      dummyInvoice.setSecDiscountTypeSelect(saleOrder.getSecDiscountTypeSelect());
      dummyInvoice.setSecDiscountAmount(saleOrder.getSecDiscountAmount());

    } else {

      dummyInvoice.setCurrency(stockMove.getCompany().getCurrency());
      dummyInvoice.setPartner(stockMove.getPartner());
      dummyInvoice.setCompany(stockMove.getCompany());
      dummyInvoice.setTradingName(stockMove.getTradingName());
      dummyInvoice.setAddress(stockMove.getToAddress());
      dummyInvoice.setAddressStr(stockMove.getToAddressStr());

      if (stockMove.getPartner().getSalePartnerPriceList() != null) {
        // Find the price list of partner
        final Set<PriceList> partnerPriceListSet =
            stockMove.getPartner().getSalePartnerPriceList().getPriceListSet();

        final PriceList priceList =
            this.findPriceList(partnerPriceListSet, stockMove.getStockMoveLineList());

        if (priceList != null) {
          dummyInvoice.setPriceList(priceList);
          dummyInvoice.setDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
          dummyInvoice.setDiscountAmount(priceList.getGeneralDiscount());
          dummyInvoice.setSecDiscountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_PERCENT);
          dummyInvoice.setSecDiscountAmount(priceList.getSecGeneralDiscount());
        }
      }
    }
    return dummyInvoice;
  }

  // Define the right price list
  protected PriceList findPriceList(
      final Set<PriceList> partnerPriceListSet, final List<StockMoveLine> stockMoveLineList) {
    PriceList priceList = null;

    switch (partnerPriceListSet.size()) {
      case 1:
        // Verify if the priceList is Activated
        final PriceList pl = partnerPriceListSet.iterator().next();
        if (pl.getIsActive()) {
          priceList = pl;
        }
        break;

      default:
        Integer activatedPriceList = 0;
        // Verify how many price list is activate
        for (final PriceList pl1 : partnerPriceListSet) {
          if (pl1.getIsActive()) {
            activatedPriceList++;
            priceList = pl1;
          }
        }
        // If the number of price list is over 1 the price cannot be found
        if (activatedPriceList > 1) {
          priceList = null;
        }

        break;
    }

    return priceList;
  }

  @Override
  @Transactional(rollbackOn = {Exception.class})
  public Optional<Invoice> createInvoiceFromMultiOutgoingStockMove(
      final List<StockMove> stockMoveList) throws AxelorException {

    if ((stockMoveList == null) || stockMoveList.isEmpty()) {
      return Optional.empty();
    }

    final Set<Address> deliveryAddressSet = new HashSet<>();

    // create dummy invoice from the first stock move
    final Invoice dummyInvoice = this.createDummyOutInvoice(stockMoveList.get(0));

    // Check if field constraints are respected
    for (final StockMove stockMove : stockMoveList) {
      this.completeInvoiceInMultiOutgoingStockMove(dummyInvoice, stockMove);
      if (stockMove.getToAddressStr() != null) {
        deliveryAddressSet.add(stockMove.getToAddress());
      }
    }

    /* check if some other fields are different and assign a default value */
    if (dummyInvoice.getAddress() == null) {
      dummyInvoice.setAddress(
          Beans.get(PartnerService.class).getInvoicingAddress(dummyInvoice.getPartner()));
      dummyInvoice.setAddressStr(
          Beans.get(AddressService.class).computeAddressStr(dummyInvoice.getAddress()));
    }

    this.fillReferenceInvoiceFromMultiOutStockMove(stockMoveList, dummyInvoice);

    final InvoiceGenerator invoiceGenerator =
        new ExtendedInvoiceGeneratorFromScratch(
            InvoiceRepository.OPERATION_TYPE_CLIENT_SALE,
            dummyInvoice.getCompany(),
            dummyInvoice.getPaymentCondition(),
            dummyInvoice.getPaymentMode(),
            dummyInvoice.getAddress(),
            dummyInvoice.getPartner(),
            dummyInvoice.getContactPartner(),
            dummyInvoice.getCurrency(),
            dummyInvoice.getPriceList(),
            dummyInvoice.getInternalReference(),
            dummyInvoice.getExternalReference(),
            dummyInvoice.getInAti(),
            null,
            dummyInvoice.getTradingName(),
            true,
            this.priceListService,
            this.invoiceLineService,
            this.invoiceService);

    Invoice invoice = invoiceGenerator.generate();
    invoice.setAddressStr(dummyInvoice.getAddressStr());

    final StringBuilder deliveryAddressStr = new StringBuilder();
    final AddressService addressService = Beans.get(AddressService.class);

    for (final Address address : deliveryAddressSet) {
      deliveryAddressStr.append(
          addressService.computeAddressStr(address).replaceAll("\n", ", ") + "\n");
    }

    invoice.setDeliveryAddressStr(deliveryAddressStr.toString());

    final List<InvoiceLine> invoiceLineList = new ArrayList<>();

    for (final StockMove stockMoveLocal : stockMoveList) {

      this.stockMoveInvoiceService.checkSplitSalePartiallyInvoicedStockMoveLines(
          stockMoveLocal, stockMoveLocal.getStockMoveLineList());
      final List<InvoiceLine> createdInvoiceLines =
          this.stockMoveInvoiceService.createInvoiceLines(
              invoice, stockMoveLocal, stockMoveLocal.getStockMoveLineList(), null);
      if (stockMoveLocal.getTypeSelect() == StockMoveRepository.TYPE_INCOMING) {
        createdInvoiceLines.forEach(this::negateInvoiceLinePrice);
      }
      invoiceLineList.addAll(createdInvoiceLines);
    }

    invoiceGenerator.populate(invoice, invoiceLineList);

    this.invoiceRepository.save(invoice);
    invoice = this.toPositivePriceInvoice(invoice);
    if ((invoice.getExTaxTotal().signum() == 0)
        && stockMoveList.stream().allMatch(StockMove::getIsReversion)) {
      invoice.setOperationTypeSelect(InvoiceRepository.OPERATION_TYPE_CLIENT_REFUND);
    }
    stockMoveList.forEach(invoice::addStockMoveSetItem);
    return Optional.of(invoice);
  }
}
