
package com.crio.warmup.stock.portfolio;

import com.crio.warmup.stock.dto.AnnualizedReturn;
import com.crio.warmup.stock.dto.Candle;
import com.crio.warmup.stock.dto.PortfolioTrade;
import com.crio.warmup.stock.dto.TiingoCandle;
import com.crio.warmup.stock.exception.StockQuoteServiceException;
import com.crio.warmup.stock.quotes.StockQuotesService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

public class PortfolioManagerImpl implements PortfolioManager {

  private RestTemplate restTemplate;
  private ObjectMapper objectMapper = getObjectMapper();
  private StockQuotesService stockQuotesService;

  protected PortfolioManagerImpl(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  protected PortfolioManagerImpl(RestTemplate restTemplate, StockQuotesService stockQuotesService) {
    this.stockQuotesService = stockQuotesService;
    this.restTemplate = restTemplate;
  }

  public PortfolioManagerImpl(StockQuotesService stockQuotesService) {
    this.stockQuotesService = stockQuotesService;
  }

  public List<Candle> getStockQuote(String symbol, LocalDate from, LocalDate to)
      throws StockQuoteServiceException {
    if (from.compareTo(to) >= 0)
      throw new RuntimeException();
    String uri = buildUri(symbol, from, to);
    String result;
    try {
      result = restTemplate.getForObject(uri, String.class);
    } catch (HttpClientErrorException e) {
      throw new StockQuoteServiceException("TooManyRequests: 429 Unknown Status Code");
    }
    TiingoCandle[] candleList;
    try {
      candleList = objectMapper.readValue(result, TiingoCandle[].class);
      if (candleList == null)
        throw new StockQuoteServiceException("Invalid Response Found");
    } catch (JsonProcessingException e) {
      throw new StockQuoteServiceException(e.getMessage());
    }
    return Arrays.asList(candleList);
  }

  private static ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }

  protected String buildUri(String symbol, LocalDate startDate, LocalDate endDate) {
    String uriTemplate = "https://api.tiingo.com/tiingo/daily/" + symbol + "/prices?" + "startDate="
        + startDate.toString() + "&endDate=" + endDate.toString() + "&token=" + getToken();
    return uriTemplate;
  }

  protected String getToken() {
    String[] token =
        {"ba213dcac06ea722d054f3150e8988f3e398b015", "61ce1dff403ba4b2b40ba2db6d6e65cec1e6c187",
            "b197a6dd821010c89ef8f1de157f9f785a1de2d7", "8f01a5a63850b10acdd7c2af98189529a3bcd575",
            "1cc10465be8655349441703e609b2e6b103b1a0a", "4cde2b644448ec2379b2147260d78783cabeda33",
            "953e5d1702c35f1aabd7a475fd8d256e2274d731", "dbafe4de20a19ef23df3deda52d513e373206cb0"};
    Random random = new Random();
    return token[random.nextInt(token.length)];
  }


  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturn(List<PortfolioTrade> portfolioTrades,
      LocalDate endDate) throws StockQuoteServiceException {
    List<AnnualizedReturn> annualizedReturns = new ArrayList<AnnualizedReturn>();
    for (PortfolioTrade obj : portfolioTrades) {
      annualizedReturns.add(getAnnualizedReturn(obj, endDate));
    }
    Collections.sort(annualizedReturns, getComparator());
    return annualizedReturns;
  }

  public AnnualizedReturn getAnnualizedReturn(PortfolioTrade obj, LocalDate endDate)
      throws StockQuoteServiceException {
    List<Candle> candleList = new ArrayList<>();
    AnnualizedReturn anRet;
    try {
      candleList =
          stockQuotesService.getStockQuote(obj.getSymbol(), obj.getPurchaseDate(), endDate);
      Candle candleObj = candleList.get(candleList.size() - 1);
      Double buyPrice = candleList.get(0).getOpen();
      Double sellPrice = candleObj.getClose();
      Double totalReturn = (sellPrice - buyPrice) / buyPrice;
      double totalNoOfYears = ChronoUnit.DAYS.between(obj.getPurchaseDate(), endDate) / 365.0;
      Double annualizedReturn = Math.pow((1 + totalReturn), (1.0 / totalNoOfYears)) - 1;
      anRet = new AnnualizedReturn(obj.getSymbol(), annualizedReturn, totalReturn);
    } catch (NullPointerException e) {
      throw new StockQuoteServiceException("Error Ocuured during Response", e.getCause());
    }
    return anRet;
  }


  private Comparator<AnnualizedReturn> getComparator() {
    return Comparator.comparing(AnnualizedReturn::getAnnualizedReturn).reversed();
  }

  @Override
  public List<AnnualizedReturn> calculateAnnualizedReturnParallel(
      List<PortfolioTrade> portfolioTrades, LocalDate endDate, int numThreads)
      throws InterruptedException, StockQuoteServiceException {

    List<AnnualizedReturn> annualizedReturns = new ArrayList<>();
    List<Future<AnnualizedReturn>> futureReturnsList = new ArrayList<>();
    final ExecutorService servicePool = Executors.newFixedThreadPool(numThreads);

    for (PortfolioTrade trade : portfolioTrades) {
      //recieving future annulized return for each product
      Callable<AnnualizedReturn> callableTask = () -> {
        return getAnnualizedReturn(trade, endDate);
      };
      Future<AnnualizedReturn> futureReturn = servicePool.submit(callableTask);
      //end of recieving 
      futureReturnsList.add(futureReturn);
    }

    for (Future<AnnualizedReturn> ftr : futureReturnsList) {
      try {
        AnnualizedReturn returns = ftr.get();
        annualizedReturns.add(returns);
      } catch (ExecutionException e) {
        throw new StockQuoteServiceException("Error when calling API");
      }
    }

    Collections.sort(annualizedReturns,getComparator());
    return annualizedReturns;
  }



}
