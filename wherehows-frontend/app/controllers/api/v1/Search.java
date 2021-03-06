/**
 * Copyright 2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package controllers.api.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.Application;
import java.util.List;
import org.apache.commons.lang3.math.NumberUtils;
import play.Logger;
import play.Play;
import play.cache.Cache;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import wherehows.dao.table.SearchDao;

import static org.apache.commons.lang3.StringUtils.*;


public class Search extends Controller {

  private static final String ELASTICSEARCH_DATASET_URL_KEY = "elasticsearch.dataset.url";
  private static final String ELASTICSEARCH_DATASET_URL =
      Play.application().configuration().getString(ELASTICSEARCH_DATASET_URL_KEY);
  private static final String WHEREHOWS_SEARCH_ENGINE_KEY = "search.engine"; // TODO: deprecated this setting
  private static final String SEARCH_ENGINE = Play.application().configuration().getString(WHEREHOWS_SEARCH_ENGINE_KEY);

  private static final String AUTOCOMPLETE_ALL_KEY = "autocomplete.all";
  private static final String AUTOCOMPLETE_DATASET_KEY = "autocomplete.dataset";
  private static final int DEFAULT_AUTOCOMPLETE_SIZE = 20;
  private static final int DEFAULT_AUTOCOMPLETE_CACHE_TIME = 3600; // cache for an hour
  private static final String DEFAULT_AUTOCOMPLETE_FIELD = "name";

  private static final SearchDao SEARCH_DAO = Application.DAO_FACTORY.getSearchDao();

  public static Result getSearchAutoComplete() {

    String input = request().getQueryString("input");
    String facet = request().getQueryString("facet");
    if (isBlank(facet)) {
      facet = DEFAULT_AUTOCOMPLETE_FIELD;
    }

    int size = 0;  // size 0 means no limit
    if (isNotBlank(input)) {
      size = NumberUtils.toInt(request().getQueryString("size"), DEFAULT_AUTOCOMPLETE_SIZE);
    }

    String cacheKey = AUTOCOMPLETE_ALL_KEY + (isNotBlank(input) ? "." + input : "-all");
    List<String> names = (List<String>) Cache.get(cacheKey);
    if (names == null || names.size() == 0) {
      names = SEARCH_DAO.getAutoCompleteList(ELASTICSEARCH_DATASET_URL, input, facet, size);
      Cache.set(cacheKey, names, DEFAULT_AUTOCOMPLETE_CACHE_TIME);
    }

    ObjectNode result = Json.newObject();
    result.put("status", "ok");
    result.put("input", input);
    result.set("source", Json.toJson(names));
    return ok(result);
  }

  public static Result getSearchAutoCompleteForDataset() {
    String input = request().getQueryString("input");

    String facet = request().getQueryString("facet");
    if (isBlank(facet)) {
      facet = DEFAULT_AUTOCOMPLETE_FIELD;
    }

    int size = 0;  // 0 means no limit
    if (isNotBlank(input)) {
      size = NumberUtils.toInt(request().getQueryString("size"), DEFAULT_AUTOCOMPLETE_SIZE);
    }

    String cacheKey = AUTOCOMPLETE_DATASET_KEY + (isNotBlank(input) ? "." + input : "-all");
    List<String> names = (List<String>) Cache.get(cacheKey);
    if (names == null || names.size() == 0) {
      names = SEARCH_DAO.getAutoCompleteListDataset(ELASTICSEARCH_DATASET_URL, input, facet, size);
      Cache.set(cacheKey, names, DEFAULT_AUTOCOMPLETE_CACHE_TIME);
    }

    ObjectNode result = Json.newObject();
    result.put("status", "ok");
    result.put("input", input);
    result.set("source", Json.toJson(names));
    return ok(result);
  }

  public static Result searchByKeyword() {
    ObjectNode result = Json.newObject();

    int page = 1;
    int size = 10;
    String keyword = request().getQueryString("keyword");
    String category = request().getQueryString("category");
    String source = request().getQueryString("source");
    String pageStr = request().getQueryString("page");
    String fabric = request().getQueryString("fabric");

    if (isBlank(pageStr)) {
      page = 1;
    } else {
      try {
        page = Integer.parseInt(pageStr);
      } catch (NumberFormatException e) {
        Logger.error("Dataset Controller searchByKeyword wrong page parameter. Error message: " + e.getMessage());
        page = 1;
      }
    }

    String sizeStr = request().getQueryString("size");
    if (isBlank(sizeStr)) {
      size = 10;
    } else {
      try {
        size = Integer.parseInt(sizeStr);
      } catch (NumberFormatException e) {
        Logger.error("Dataset Controller searchByKeyword wrong page parameter. Error message: " + e.getMessage());
        size = 10;
      }
    }

    result.put("status", "ok");
    Boolean isDefault = false;
    if (isBlank(category)) {
      category = "datasets";
    }
    // Filter on platform
    if (isBlank(source) || source.equalsIgnoreCase("all") || source.equalsIgnoreCase("default")) {
      source = null;
    }

    // Filter on fabric
    if (isBlank(fabric) || fabric.equalsIgnoreCase("all") || fabric.equalsIgnoreCase("default")) {
      fabric = null;
    }

    result.set("result",
        SEARCH_DAO.elasticSearchDatasetByKeyword(ELASTICSEARCH_DATASET_URL, category, keyword, source, page, size, fabric));

    return ok(result);
  }
}
