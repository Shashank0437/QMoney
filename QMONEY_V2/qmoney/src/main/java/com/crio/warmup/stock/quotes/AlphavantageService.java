
package com.crio.warmup.stock.quotes;

import com.crio.warmup.stock.dto.AlphavantageCandle;
import com.crio.warmup.stock.dto.AlphavantageDailyResponse;
import com.crio.warmup.stock.dto.Candle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import org.springframework.web.client.RestTemplate;

public class AlphavantageService implements StockQuotesService {

  public AlphavantageService(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
      throws StockQuoteServiceException {
    List<Candle> candles = new ArrayList<>();
    String uri = buildUri(symbol);
    try {
      String result = restTemplate.getForObject(uri, String.class);
      AlphavantageDailyResponse response =
          objectMapper.readValue(result, new TypeReference<AlphavantageDailyResponse>() {});
      for (java.util.Map.Entry<LocalDate, AlphavantageCandle> entry : response.getCandles()
          .entrySet()) {
        LocalDate date = entry.getKey();
        if (date.compareTo(from) >= 0 && date.compareTo(to) <= 0) {
          entry.getValue().setDate(date);
          candles.add(entry.getValue());
        }
      }
      Collections.reverse(candles);
    } catch (NullPointerException | JsonProcessingException e) {
      throw new StockQuoteServiceException("AlphaVantage Returned Invalid Response", e);
    }
    return candles;
  }

  private RestTemplate restTemplate;
  private ObjectMapper objectMapper = getObjectMapper();

  private static ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }

  protected String buildUri(String symbol) {
    String uriTemplate = "https://www.alphavantage.co/"
        + "query?function=TIME_SERIES_DAILY&apikey=G0CKBB6TDFUK3WYN&outputsize=full&symbol="
        + symbol;
    return uriTemplate;
  }
}

