
package com.crio.warmup.stock;

import com.crio.warmup.stock.dto.*;
import com.crio.warmup.stock.log.UncaughtExceptionHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.crio.warmup.stock.portfolio.PortfolioManager;
import com.crio.warmup.stock.portfolio.PortfolioManagerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.web.client.RestTemplate;



public class PortfolioManagerApplication {
  
  
  public static List<String> mainReadFile(String[] args) throws IOException, URISyntaxException {
    File file = resolveFileFromResources(args[0]);
    ObjectMapper objectMapper = getObjectMapper();
    PortfolioTrade[] trades = objectMapper.readValue(file, PortfolioTrade[].class);
    List<String> symbols = new ArrayList<>();
    for (PortfolioTrade t : trades) {
      symbols.add(t.getSymbol());
    }
    return symbols;
  }

 
  private static void printJsonObject(Object object) throws IOException {
    Logger logger = Logger.getLogger(PortfolioManagerApplication.class.getCanonicalName());
    ObjectMapper mapper = new ObjectMapper();
    logger.info(mapper.writeValueAsString(object));
  }

  private static File resolveFileFromResources(String filename) throws URISyntaxException {
    return Paths.get(Thread.currentThread().getContextClassLoader().getResource(filename).toURI())
        .toFile();
  }

  private static ObjectMapper getObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }

  public static List<String> debugOutputs() {
    String valueOfArgument0 = "trades.json";
    String resultOfResolveFilePathArgs0 =
        "/home/crio-user/workspace/kshashank391999-ME_QMONEY_V2/qmoney/bin/main/trades.json";
    String toStringOfObjectMapper = "com.fasterxml.jackson.databind.ObjectMapper@5542c4ed";
    String functionNameFromTestFileInStackTrace = "PortfolioManagerApplication.mainReadFile()";
    String lineNumberFromTestFileInStackTrace = "29:1";

    return Arrays.asList(
        new String[] {valueOfArgument0, resultOfResolveFilePathArgs0, toStringOfObjectMapper,
            functionNameFromTestFileInStackTrace, lineNumberFromTestFileInStackTrace});
  }


  public static List<String> mainReadQuotes(String[] args) throws IOException, URISyntaxException {
    File file = resolveFileFromResources(args[0]);
    ObjectMapper objectmapper = getObjectMapper();
    List<PortfolioTrade> allObjects =
        objectmapper.readValue(file, new TypeReference<List<PortfolioTrade>>() {});
    List<String> allSymbols = new ArrayList<String>();
    List<TotalReturnsDto> mappingList = getSortedClosingPrice(objectmapper, allObjects, args[1]);
    mappingList.sort(Comparator.comparing(TotalReturnsDto::getClosingPrice));
    for (TotalReturnsDto trDto : mappingList) {
      allSymbols.add(trDto.getSymbol());
    }
    return allSymbols;
  }

  private static List<TotalReturnsDto> getSortedClosingPrice(ObjectMapper objectMapper,
      List<PortfolioTrade> allObjects, String arg)
      throws JsonMappingException, JsonProcessingException {
    RestTemplate restTemplate = new RestTemplate();
    List<TotalReturnsDto> mappingList = new ArrayList<TotalReturnsDto>();
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-d");
    for (PortfolioTrade obj : allObjects) {
      String uri = prepareUrl(obj, LocalDate.parse(arg, formatter),
          getToken());
      String result = (restTemplate.getForObject(uri, String.class));
      List<TiingoCandle> candleList =
          objectMapper.readValue(result, new TypeReference<List<TiingoCandle>>() {});
      TiingoCandle candleObj = candleList.get(candleList.size() - 1);
      TotalReturnsDto trDto = new TotalReturnsDto(obj.getSymbol(), candleObj.getClose());
      mappingList.add(trDto);
    }
    return mappingList;
  }
  
  public static List<PortfolioTrade> readTradesFromJson(String filename)
      throws IOException, URISyntaxException {
    File file = resolveFileFromResources(filename);
    ObjectMapper objectMapper = getObjectMapper();
    PortfolioTrade[] trades = objectMapper.readValue(file, PortfolioTrade[].class);
    List<PortfolioTrade> symbols = new ArrayList<>();
    for (PortfolioTrade t : trades) {
      symbols.add(t);
    }
    return symbols;
  }

  public static String prepareUrl(PortfolioTrade trade, LocalDate endDate, String token) {
    String uri = "https://api.tiingo.com/tiingo/daily/" + trade.getSymbol() + "/prices?startDate="
        + trade.getPurchaseDate().toString() + "&endDate=" + endDate.toString() + "&token=" + token;
    return uri;
  }

  public static String getToken() {
    return "f138a3010fe1981e894e06019ed1062f51750f61";
  }

  static Double getOpeningPriceOnStartDate(List<Candle> candles) {
    candles.sort(Comparator.comparing(Candle::getOpen));
    return (candles.get(0)).getOpen();
  }

  public static Double getClosingPriceOnEndDate(List<Candle> candles) {
    candles.sort(Comparator.comparing(Candle::getClose));
    return (candles.get(candles.size() - 1)).getClose();
  }

  public static List<Candle> fetchCandles(PortfolioTrade trade, LocalDate endDate, String token)
      throws JsonMappingException, JsonProcessingException {
    String uri = prepareUrl(trade, endDate, getToken());
    String result = new RestTemplate().getForObject(uri, String.class);
    ObjectMapper objectmapper = getObjectMapper();
    return Arrays.asList(objectmapper.readValue(result, TiingoCandle[].class));
  }

  public static List<AnnualizedReturn> mainCalculateSingleReturn(String[] args)
      throws IOException, URISyntaxException {
    File file = resolveFileFromResources(args[0]);
    RestTemplate restTemplate = new RestTemplate();
    ObjectMapper objectMapper = getObjectMapper();
    List<PortfolioTrade> allJsonObjects =
        objectMapper.readValue(file, new TypeReference<List<PortfolioTrade>>() {});
    List<AnnualizedReturn> annualizedReturns = new ArrayList<AnnualizedReturn>();
    for (PortfolioTrade obj : allJsonObjects) {
      String uri = prepareUrl(obj,
          LocalDate.parse(args[1], DateTimeFormatter.ofPattern("yyyy-MM-d")), getToken());
      String result = (restTemplate.getForObject(uri, String.class));
      List<TiingoCandle> candleList =
          objectMapper.readValue(result, new TypeReference<List<TiingoCandle>>() {});
      TiingoCandle candleObj = candleList.get(candleList.size() - 1);
      Double buyPrice = candleList.get(0).getOpen();
      Double sellPrice = candleObj.getClose();
      AnnualizedReturn anRet =
          calculateAnnualizedReturns(candleObj.getDate(), obj, buyPrice, sellPrice);
      annualizedReturns.add(anRet);
    }
    annualizedReturns.sort(Comparator.comparing(AnnualizedReturn::getAnnualizedReturn));
    Collections.reverse(annualizedReturns);
    return annualizedReturns;
  }

  public static AnnualizedReturn calculateAnnualizedReturns(LocalDate endDate, PortfolioTrade trade,
      Double buyPrice, Double sellPrice) {
    Double totalReturn = (sellPrice - buyPrice) / buyPrice;
    double totalNoOfYears = ChronoUnit.DAYS.between(trade.getPurchaseDate(), endDate) / 365.0;
    Double annualizedReturn = Math.pow((1 + totalReturn), (1.0 / totalNoOfYears)) - 1;
    return new AnnualizedReturn(trade.getSymbol(), annualizedReturn, totalReturn);
  }

  public static List<AnnualizedReturn> mainCalculateReturnsAfterRefactor(String[] args)
      throws Exception {
    File file = resolveFileFromResources(args[0]);
    LocalDate endDate = LocalDate.parse(args[1]);
    ObjectMapper objectMapper = getObjectMapper();
    RestTemplate restTemplate = new RestTemplate();
    PortfolioTrade[] portfolioTrades = objectMapper.readValue(file, PortfolioTrade[].class);
    PortfolioManager portfolioManager = PortfolioManagerFactory.getPortfolioManager(restTemplate);
    return portfolioManager.calculateAnnualizedReturn(Arrays.asList(portfolioTrades), endDate);
  }
  
  public static void main(String[] args) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler());
    ThreadContext.put("runId", UUID.randomUUID().toString());

    // printJsonObject(mainReadFile(args));
    //printJsonObject(mainReadQuotes(args));
    //printJsonObject(mainCalculateSingleReturn(args));
      printJsonObject(mainCalculateReturnsAfterRefactor(args));

  }
}

