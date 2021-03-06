/*
 * Copyright (C) 2012-2016 The Android Money Manager Ex Project Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.money.manager.ex.investment.yql;

import android.app.ProgressDialog;
import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.money.manager.ex.R;
import com.money.manager.ex.core.ExceptionHandler;
import com.money.manager.ex.core.NumericHelper;
import com.money.manager.ex.investment.ISecurityPriceUpdater;
import com.money.manager.ex.investment.PriceUpdaterBase;
import com.money.manager.ex.investment.SecurityPriceModel;
import com.money.manager.ex.investment.SecurityPriceUpdaterFactory;
import com.money.manager.ex.investment.events.PriceDownloadedEvent;
import com.money.manager.ex.utils.DialogUtils;
import com.money.manager.ex.utils.MyDateTimeUtils;

import org.greenrobot.eventbus.EventBus;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.List;

import info.javaperformance.money.Money;
import info.javaperformance.money.MoneyFactory;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Updates security prices from Yahoo Finance using YQL. Using Retrofit for network access.
 */
public class YqlSecurityPriceUpdaterRetrofit
    extends PriceUpdaterBase
    implements ISecurityPriceUpdater {

    /**
     *
     * @param context Executing context
     */
    public YqlSecurityPriceUpdaterRetrofit(Context context) {
        super(context);
    }

    // https://query.yahooapis.com/v1/public/yql
    // ?q=... url escaped
    // &format=json
    // &diagnostics=true
    // &env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys
    // &callback=

    /**
     * Update prices for all the symbols in the list.
     */
    public void downloadPrices(List<String> symbols) {
        if (symbols == null) return;
        int items = symbols.size();
        if (items == 0) return;

        showProgressDialog(items);

        YqlQueryGenerator queryGenerator = new YqlQueryGenerator();
        String query = queryGenerator.getQueryFor(symbols);

        IYqlService yql = getYqlService();

        // Async response handler.
        Callback<JsonElement> callback = new Callback<JsonElement>() {
            @Override
            public void onResponse(Call<JsonElement> call, Response<JsonElement> response) {
                onContentDownloaded(response.body());
            }

            @Override
            public void onFailure(Call<JsonElement> call, Throwable t) {
                ExceptionHandler handler = new ExceptionHandler(getContext(), this);
                handler.handle(t, "fetching price");
                closeProgressDialog();
            }
        };

        try {
            // This would be the synchronous call.
//            prices = yql.getPrices(query).execute().body();
            yql.getPrices(query).enqueue(callback);
        } catch (Exception e) {
            ExceptionHandler handler = new ExceptionHandler(getContext(), this);
            handler.handle(e, "fetching prices");
        }
    }

    /**
     * Called when the file is downloaded and the contents read.
     * Here we have all the prices.
     */
    public void onContentDownloaded(JsonElement response) {
        ExceptionHandler handler = new ExceptionHandler(getContext(), this);

        if (response == null) {
            handler.showMessage(R.string.error_updating_rates);
            closeProgressDialog();
            return;
        }

        // parse Json results
        List<SecurityPriceModel> pricesList = getPricesFromJson(response.getAsJsonObject());
        if (pricesList == null) {
            handler.showMessage(R.string.error_no_price_found_for_symbol);
        } else {
            // Send the parsed price data to the listener(s).
            for (SecurityPriceModel model : pricesList) {
                // Notify the caller.
                EventBus.getDefault().post(new PriceDownloadedEvent(model.symbol, model.price, model.date));
            }
        }
        closeProgressDialog();

        // Notify user that all the prices have been downloaded.
        handler.showMessage(R.string.download_complete);
    }

    private List<SecurityPriceModel> getPricesFromJson(JsonObject root) {
        ArrayList<SecurityPriceModel> result = new ArrayList<>();

        // check whether there is only one item or more
        JsonElement results = root.get("query").getAsJsonObject()
                .get("results");
        if (results == null) return null;

        JsonElement quoteElement = results.getAsJsonObject().get("quote");
        if (quoteElement instanceof JsonArray) {
            JsonArray quotes = quoteElement.getAsJsonArray();

            for (int i = 0; i < quotes.size(); i++) {
                JsonObject quote = quotes.get(i).getAsJsonObject();
                // process individual quote
                SecurityPriceModel priceModel = getSecurityPriceFor(quote);
                if (priceModel == null) continue;

                result.add(priceModel);
            }
        } else {
            // Single quote
            JsonObject quote = quoteElement.getAsJsonObject();

            SecurityPriceModel priceModel = getSecurityPriceFor(quote);
            if (priceModel != null) {
                result.add(priceModel);
            }
        }

        return result;
    }

    private SecurityPriceModel getSecurityPriceFor(JsonObject quote) {
        SecurityPriceModel priceModel = new SecurityPriceModel();
        priceModel.symbol = quote.get("symbol").getAsString();

        ExceptionHandler handler = new ExceptionHandler(getContext(), this);

        // Price

        JsonElement priceElement = quote.get("LastTradePriceOnly");
        if (priceElement == JsonNull.INSTANCE) {
            handler.showMessage(getContext().getString(R.string.error_no_price_found_for_symbol) + " " + priceModel.symbol);
            return null;
        }
        String priceString = priceElement.getAsString();
        if (!NumericHelper.isNumeric(priceString)) {
            handler.showMessage(getContext().getString(R.string.error_no_price_found_for_symbol) + " " + priceModel.symbol);
            return null;
        }

        Money price = MoneyFactory.fromString(priceString);
        /**
        LSE stocks are expressed in GBp (pence), not Pounds.
        From stockspanel.cpp, line 785: if (StockQuoteCurrency == "GBp") dPrice = dPrice / 100;
         */
        JsonElement currencyElement = quote.get("Currency");
        if (currencyElement != null) {
            String currency;
            try {
                currency = currencyElement.getAsString();
            } catch (UnsupportedOperationException ex) {
                handler.handle(ex, "reading currency from downloaded price");
                currency = "";
            }
            if (currency.equals("GBp")) {
                price = price.divide(100, MoneyFactory.MAX_ALLOWED_PRECISION);
            }
        }
        priceModel.price = price;

        // Date

        DateTime date = MyDateTimeUtils.today();
        JsonElement dateElement = quote.get("LastTradeDate");
        if (dateElement != JsonNull.INSTANCE) {
            // Sometimes the date is not available. For now we will use today's date.
            DateTimeFormatter format = DateTimeFormat.forPattern("MM/dd/yyyy");
            date = format.parseDateTime(dateElement.getAsString());
        }
        priceModel.date = date;

        return priceModel;
    }

    public IYqlService getYqlService() {
        String BASE_URL = "https://query.yahooapis.com";

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .baseUrl(BASE_URL)
                .build();
        return retrofit.create(IYqlService.class);
    }

}
