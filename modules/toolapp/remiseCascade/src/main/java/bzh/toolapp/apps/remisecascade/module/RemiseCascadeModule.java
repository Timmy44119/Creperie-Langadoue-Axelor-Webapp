/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2021 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package bzh.toolapp.apps.remisecascade.module;

import com.axelor.app.AxelorModule;
import com.axelor.apps.base.service.PriceListService;
import com.axelor.apps.businessproduction.service.SaleOrderLineBusinessProductionServiceImpl;
import com.axelor.apps.businessproject.service.InvoiceLineProjectServiceImpl;
import com.axelor.apps.cash.management.service.InvoiceServiceManagementImpl;
import com.axelor.apps.supplychain.service.SaleOrderComputeServiceSupplychainImpl;

import bzh.toolapp.apps.remisecascade.service.ExtendedInvoiceLineServiceImpl;
import bzh.toolapp.apps.remisecascade.service.ExtendedInvoiceServiceImpl;
import bzh.toolapp.apps.remisecascade.service.ExtendedPriceListServiceImpl;
import bzh.toolapp.apps.remisecascade.service.ExtendedSaleOrderComputeServiceImpl;
import bzh.toolapp.apps.remisecascade.service.ExtendedSaleOrderLineServiceImpl;

public class RemiseCascadeModule extends AxelorModule {

	@Override
	protected void configure() {
		this.bind(SaleOrderComputeServiceSupplychainImpl.class).to(ExtendedSaleOrderComputeServiceImpl.class);
		this.bind(SaleOrderLineBusinessProductionServiceImpl.class)
		.to(ExtendedSaleOrderLineServiceImpl.class);
		this.bind(PriceListService.class).to(ExtendedPriceListServiceImpl.class);
		this.bind(InvoiceLineProjectServiceImpl.class).to(ExtendedInvoiceLineServiceImpl.class);
		this.bind(InvoiceServiceManagementImpl.class).to(ExtendedInvoiceServiceImpl.class);
	}
}
