package bzh.toolapp.apps.remisecascade.service.pricelist;

import com.axelor.apps.account.service.invoice.generator.InvoiceGenerator;
import com.axelor.apps.base.db.PriceList;
import com.axelor.apps.base.db.PriceListLine;
import com.axelor.apps.base.db.Product;
import com.axelor.apps.base.db.repo.AppBaseRepository;
import com.axelor.apps.base.db.repo.PriceListLineRepository;
import com.axelor.apps.base.db.repo.PriceListRepository;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.base.service.app.AppBaseService;
import com.google.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtendedPriceListServiceImpl extends PriceListService {

  @Inject protected AppBaseService appBaseService;
  private final Logger logger = LoggerFactory.getLogger(InvoiceGenerator.class);

  @Override
  public BigDecimal getUnitPriceDiscounted(
      final PriceListLine priceListLine, final BigDecimal unitPrice) {
    BigDecimal targetPrice;

    switch (priceListLine.getTypeSelect()) {
      case PriceListLineRepository.TYPE_ADDITIONNAL:
        if (priceListLine.getAmountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_FIXED) {
          targetPrice = unitPrice.add(priceListLine.getAmount());
        } else if (priceListLine.getAmountTypeSelect()
            == PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
          targetPrice =
              unitPrice.multiply(
                  BigDecimal.ONE.add(priceListLine.getAmount().divide(new BigDecimal(100))));
        } else {

          targetPrice = unitPrice;
        }
        break;
      case PriceListLineRepository.TYPE_DISCOUNT:
        // first discount amount (shared with every context, sale or purchase)
        if (priceListLine.getAmountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_FIXED) {
          targetPrice = unitPrice.subtract(priceListLine.getAmount());
        } else if (priceListLine.getAmountTypeSelect()
            == PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
          targetPrice =
              unitPrice.multiply(
                  BigDecimal.ONE.subtract(priceListLine.getAmount().divide(new BigDecimal(100))));
        } else {
          targetPrice = unitPrice;
        }
        // second discount amount
        if (priceListLine.getSecAmountTypeSelect() == PriceListLineRepository.AMOUNT_TYPE_FIXED) {
          targetPrice = targetPrice.subtract(priceListLine.getSecAmount());
        } else if (priceListLine.getSecAmountTypeSelect()
            == PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
          targetPrice =
              targetPrice.multiply(
                  BigDecimal.ONE.subtract(
                      priceListLine.getSecAmount().divide(new BigDecimal(100))));
        }

        final PriceList priceList = priceListLine.getPriceList();
        if (priceList != null) {
          // first global discount amount
          final BigDecimal generalDiscount = priceList.getGeneralDiscount();
          if (generalDiscount != BigDecimal.ZERO) {
            targetPrice =
                targetPrice.multiply(
                    BigDecimal.ONE.subtract(generalDiscount.divide(new BigDecimal(100))));
          }
          // second global discount amount
          final BigDecimal secondGeneralDiscount = priceList.getSecGeneralDiscount();
          if (secondGeneralDiscount != BigDecimal.ZERO) {
            targetPrice =
                targetPrice.multiply(
                    BigDecimal.ONE.subtract(secondGeneralDiscount.divide(new BigDecimal(100))));
          }
        }
        break;
      case PriceListLineRepository.TYPE_REPLACE:
        targetPrice = priceListLine.getAmount();
        break;
      default:
        targetPrice = unitPrice;
    }
    return targetPrice;
  }

  @Override
  public Map<String, Object> getReplacedPriceAndDiscounts(
      final PriceList priceList, final PriceListLine priceListLine, BigDecimal price) {
    int discountTypeSelect = 0;

    if (priceListLine != null) {
      discountTypeSelect = priceListLine.getTypeSelect();
    }
    final Map<String, Object> discounts = this.getDiscounts(priceList, priceListLine, price);
    if (discounts != null) {
      final int computeMethodDiscountSelect =
          this.appBaseService.getAppBase().getComputeMethodDiscountSelect();
      if (((computeMethodDiscountSelect == AppBaseRepository.INCLUDE_DISCOUNT_REPLACE_ONLY)
              && (discountTypeSelect == PriceListLineRepository.TYPE_REPLACE))
          || (computeMethodDiscountSelect == AppBaseRepository.INCLUDE_DISCOUNT)) {

        price = this.computeExtendedDiscount(price, discounts, priceList.getTypeSelect());
        discounts.put("price", price);
        discounts.put("discountTypeSelect", PriceListLineRepository.AMOUNT_TYPE_NONE);
        discounts.put("discountAmount", BigDecimal.ZERO);
      }
    }
    return discounts;
  }

  /**
   * Here, we will manage two cases :
   *
   * <p>For purchase, we continue to use only one discount (per line or global)
   *
   * <p>For invoice and sale, we have to manage 2 discounts per line and 2 discounts globally.
   *
   * @param price original element to modify.
   * @param discounts
   * @param typeSelection could be Purchase or Sale
   * @return a price adapted with discounts information.
   */
  private BigDecimal computeExtendedDiscount(
      final BigDecimal price, final Map<String, Object> discounts, final Integer typeSelection) {
    BigDecimal targetPrice;
    if (PriceListRepository.TYPE_SALE == typeSelection) {
      // custom behavior for invoice and sale
      targetPrice = price;
      targetPrice =
          this.computeDiscount(
              targetPrice,
              (int) discounts.get(PriceListConstants.LINE_DISCOUNT_TYPE_SELECT),
              (BigDecimal) discounts.get(PriceListConstants.LINE_DISCOUNT_AMOUNT));
      targetPrice =
          this.computeDiscount(
              targetPrice,
              (int) discounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_TYPE_SELECT),
              (BigDecimal) discounts.get(PriceListConstants.LINE_SECOND_DISCOUNT_AMOUNT));
      targetPrice =
          this.computeDiscount(
              targetPrice,
              (int) discounts.get(PriceListConstants.GLOBAL_DISCOUNT_TYPE_SELECT),
              (BigDecimal) discounts.get(PriceListConstants.GLOBAL_DISCOUNT_AMOUNT));
      targetPrice =
          this.computeDiscount(
              targetPrice,
              (int) discounts.get(PriceListConstants.GLOBAL_SECOND_DISCOUNT_TYPE_SELECT),
              (BigDecimal) discounts.get(PriceListConstants.GLOBAL_SECOND_DISCOUNT_AMOUNT));
    } else {
      // classic behavior for purchase
      targetPrice =
          this.computeDiscount(
              price,
              (int) discounts.get("discountTypeSelect"),
              (BigDecimal) discounts.get("discountAmount"));
    }
    return targetPrice;
  }

  @Override
  public Map<String, Object> getDiscounts(
      final PriceList priceList, final PriceListLine priceListLine, final BigDecimal price) {

    final Map<String, Object> discounts = new HashMap<>();

    if (priceListLine != null) {
      // basic code for purchase
      discounts.put(
          "discountAmount",
          this.getDiscountAmount(priceListLine, price).setScale(2, RoundingMode.HALF_UP));
      discounts.put("discountTypeSelect", this.getDiscountTypeSelect(priceListLine));

      // extended code for invoice and sale
      discounts.put(
          PriceListConstants.LINE_DISCOUNT_AMOUNT,
          this.getDiscountAmount(priceListLine, price).setScale(2, RoundingMode.HALF_UP));
      discounts.put(
          PriceListConstants.LINE_DISCOUNT_TYPE_SELECT, this.getDiscountTypeSelect(priceListLine));
      discounts.put(
          PriceListConstants.LINE_SECOND_DISCOUNT_AMOUNT,
          this.getSecDiscountAmount(priceListLine, price).setScale(2, RoundingMode.HALF_UP));
      discounts.put(
          PriceListConstants.LINE_SECOND_DISCOUNT_TYPE_SELECT,
          priceListLine.getSecAmountTypeSelect());
    } else {
      // basic code for purchase
      final BigDecimal discountAmount =
          priceList.getGeneralDiscount().setScale(2, RoundingMode.HALF_UP);
      discounts.put("discountAmount", discountAmount);
      if (discountAmount.compareTo(BigDecimal.ZERO) == 0) {
        discounts.put("discountTypeSelect", PriceListLineRepository.AMOUNT_TYPE_NONE);
      } else {
        discounts.put("discountTypeSelect", PriceListLineRepository.AMOUNT_TYPE_PERCENT);
      }
    }

    // extends code for global discounts for invoice and sale
    discounts.put(
        PriceListConstants.GLOBAL_DISCOUNT_AMOUNT,
        priceList.getGeneralDiscount().setScale(2, RoundingMode.HALF_UP));
    discounts.put(
        PriceListConstants.GLOBAL_DISCOUNT_TYPE_SELECT,
        this.computeDiscountTypeSelection(priceList.getGeneralDiscount()));
    discounts.put(
        PriceListConstants.GLOBAL_SECOND_DISCOUNT_AMOUNT,
        priceList.getSecGeneralDiscount().setScale(2, RoundingMode.HALF_UP));
    discounts.put(
        PriceListConstants.GLOBAL_SECOND_DISCOUNT_TYPE_SELECT,
        this.computeDiscountTypeSelection(priceList.getSecGeneralDiscount()));

    return discounts;
  }

  private int computeDiscountTypeSelection(final BigDecimal discountAmount) {
    int selection = PriceListLineRepository.AMOUNT_TYPE_PERCENT;
    if (discountAmount.compareTo(BigDecimal.ZERO) == 0) {
      selection = PriceListLineRepository.AMOUNT_TYPE_NONE;
    }
    return selection;
  }

  private BigDecimal getSecDiscountAmount(
      final PriceListLine priceListLine, final BigDecimal unitPrice) {
    switch (priceListLine.getTypeSelect()) {
      case PriceListLineRepository.TYPE_ADDITIONNAL:
        return priceListLine.getSecAmount().negate();

      case PriceListLineRepository.TYPE_DISCOUNT:
        return priceListLine.getSecAmount();

      case PriceListLineRepository.TYPE_REPLACE:
        return unitPrice.subtract(priceListLine.getSecAmount());

      default:
        return BigDecimal.ZERO;
    }
  }

  /**
   * This method will retrieve a {@link PriceListLine} only if quantity of product is more than
   * first discount quantity. Otherwise, it returns null.
   *
   * <p>We will filter manually second discount by replacing existing values if needed.
   */
  @Override
  public PriceListLine getPriceListLine(
      final Product product,
      final BigDecimal qty,
      final PriceList priceList,
      final BigDecimal price) {
    final PriceListLine priceListLineTemp = new PriceListLine();
    final PriceListLine priceListLine = super.getPriceListLine(product, qty, priceList, price);

    if (priceListLine != null) {
      priceListLineTemp.setId(priceListLine.getId());
      priceListLineTemp.setTypeSelect(priceListLine.getTypeSelect());
      priceListLineTemp.setProduct(priceListLine.getProduct());
      priceListLineTemp.setProductCategory(priceListLine.getProductCategory());
      priceListLineTemp.setAmount(priceListLine.getAmount());
      priceListLineTemp.setAmountTypeSelect(priceListLine.getAmountTypeSelect());
      priceListLineTemp.setMinQty(priceListLine.getMinQty());

      // check activation of second discount
      if (priceListLine.getSecAmountTypeSelect() != PriceListLineRepository.AMOUNT_TYPE_NONE) {
        if (priceListLine.getSecMinQty().compareTo(qty) > 0) {
          // We have to disable manually information about second discount to avoid
          // activation
          priceListLineTemp.setSecAmountTypeSelect(PriceListLineRepository.AMOUNT_TYPE_NONE);
          priceListLineTemp.setSecAmount(BigDecimal.ZERO);
        } else {
          priceListLineTemp.setSecTypeSelect(priceListLine.getSecTypeSelect());
          priceListLineTemp.setSecAmountTypeSelect(priceListLine.getSecAmountTypeSelect());
          priceListLineTemp.setSecAmount(priceListLine.getSecAmount());
        }
      }
    }
    return priceListLineTemp;
  }

  @Override
  public BigDecimal computeDiscount(
      final BigDecimal unitPrice, final int discountTypeSelect, final BigDecimal discountAmount) {
    // Change the scale on 3 digits
    if (discountTypeSelect == PriceListLineRepository.AMOUNT_TYPE_FIXED) {
      return unitPrice.subtract(discountAmount).setScale(3, RoundingMode.HALF_UP);
    }
    // Change the scale on 3 digits
    if (discountTypeSelect == PriceListLineRepository.AMOUNT_TYPE_PERCENT) {
      return unitPrice
          .multiply(new BigDecimal(100).subtract(discountAmount))
          .divide(new BigDecimal(100), 3, RoundingMode.HALF_UP);
    }

    return unitPrice;
  }
}
