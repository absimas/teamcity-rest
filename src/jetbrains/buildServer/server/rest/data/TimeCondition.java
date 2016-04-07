/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.server.rest.data;

import java.util.*;
import jetbrains.buildServer.ServiceLocator;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.impl.RunningBuildsManagerEx;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.util.TimeService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 23/11/2015
 */
public class TimeCondition {
  public static final String DATE_CONDITION_EQUALS = "equals";
  public static final String DATE_CONDITION_BEFORE = "before";
  public static final String DATE_CONDITION_AFTER = "after";
  static final Map<String, Condition<Date>> ourTimeConditions = new HashMap<String, Condition<Date>>();
  protected static final String DATE = "date";
  protected static final String BUILD = "build";
  protected static final String CONDITION = "condition";
  protected static final String INCLUDE_INITIAL = "includeInitial";
  protected static final String SHIFT = "shift";

  static {
    ourTimeConditions.put(TimeCondition.DATE_CONDITION_EQUALS, new TimeCondition.Condition<Date>() {
      @Override
      public boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
        //noinspection SimplifiableConditionalExpression
        return refDate == null ? false : tryDate.equals(refDate);
      }
    });
    ourTimeConditions.put(TimeCondition.DATE_CONDITION_AFTER, new TimeCondition.Condition<Date>() {
      @Override
      public boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
        //noinspection SimplifiableConditionalExpression
        return refDate == null ? false : tryDate.after(refDate);
      }
    });
    ourTimeConditions.put(DATE_CONDITION_BEFORE, new TimeCondition.Condition<Date>() {
      @Override
      public boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
        //noinspection SimplifiableConditionalExpression
        return refDate == null ? true : tryDate.before(refDate);
      }
    });
  }

  @NotNull private final TimeService myTimeService;
  @NotNull private final ServiceLocator myServiceLocator;
  @Nullable private final ValueExtractor<BuildPromotion, Date> myDefaultBuildValueExtractor;

  public TimeCondition(@NotNull final ServiceLocator serviceLocator) {
    //need BuildPromotionFinder, but do not use that because of the cyclic dependency
    myServiceLocator = serviceLocator;
    myDefaultBuildValueExtractor = STARTED_BUILD_TIME;
    myTimeService = serviceLocator.getSingletonService(RunningBuildsManagerEx.class).getTimeService();
  }

  private BuildPromotionFinder myBuildPromotionFinder;

  @NotNull
  private BuildPromotionFinder getBuildPromotionFinder() {
    if (myBuildPromotionFinder == null) {
      myBuildPromotionFinder = myServiceLocator.getSingletonService(BuildPromotionFinder.class);
    }
    return myBuildPromotionFinder;
  }

  /**
    * @return Date is included if it can be used for cutting processing. 'null' if no dimension is defined
    */
   @Nullable
   <T> FilterAndLimitingDate<T> processTimeConditions(@NotNull final String locatorDimension,
                                                      @NotNull final Locator locator,
                                                      @NotNull final ValueExtractor<T, Date> valueExtractor) {
     return processTimeConditions(locatorDimension, locator, valueExtractor, null);
   }

  /**
   * @return Date is included if it can be used for cutting processing. 'null' if no dimension is defined
   */
  @Nullable
  <T> FilterAndLimitingDate<T> processTimeConditions(@NotNull final String locatorDimension,
                                                     @NotNull final Locator locator,
                                                     @NotNull final ValueExtractor<T, Date> valueExtractor,
                                                     @Nullable final ValueExtractor<BuildPromotion, Date> buildValueExtractor) {
    final List<String> timeLocators = locator.getDimensionValue(locatorDimension);
    if (timeLocators.isEmpty())
      return null;
    AndedFilter<T> resultFilter = new AndedFilter<>();
    Date resultDate = null;
    for (String timeLocator : timeLocators) {
      try {
        FilterAndLimitingDate<T> filterAndLimitingDate = processTimeCondition(timeLocator, valueExtractor, buildValueExtractor);
        resultFilter.add(filterAndLimitingDate.getFilter());
        resultDate = maxDate(resultDate, filterAndLimitingDate.getLimitingDate());
      } catch (BadRequestException e) {
        throw new BadRequestException("Error processing '" + locatorDimension + "' locator '" + timeLocator + "': " + e.getMessage(), e);
      }
    }
    return new FilterAndLimitingDate<T>(resultFilter, resultDate);
  }

  /**
   * @return Date if it can be used for cutting builds processing
   */
  @NotNull
  private <T> FilterAndLimitingDate<T> processTimeCondition(@NotNull final String timeLocatorText,
                                                            @NotNull final ValueExtractor<T, Date> valueExtractor,
                                                            @Nullable final ValueExtractor<BuildPromotion, Date> buildValueExtractor) {
    ParsedTimeCondition matcher;
    if (buildValueExtractor == null) {
      matcher = getTimeCondition(timeLocatorText);
    }else{
      matcher = getTimeCondition(timeLocatorText, buildValueExtractor);
    }
    FilterConditionChecker<T> filter = new FilterConditionChecker<T>() {
      @Override
      public boolean isIncluded(@NotNull final T item) {
        final Date tryValue = valueExtractor.get(item);
        if (tryValue == null) {
          return false; //do not include if no date present (e.g. not started build). This can be reworked to treat nulls as "future" instead of "never"
        }
        return matcher.matches(tryValue);
      }
    };
    return new FilterAndLimitingDate<T>(filter, matcher.getLimitingSinceDate());
  }

  @NotNull
  public ParsedTimeCondition getTimeCondition(@NotNull final String timeLocatorText) {
    return getTimeCondition(timeLocatorText, myDefaultBuildValueExtractor);
  }

  @NotNull
  private ParsedTimeCondition getTimeCondition(@NotNull final String timeLocatorText, @Nullable final ValueExtractor<BuildPromotion, Date> buildValueExtractor) {
    @NotNull TimeWithPrecision limitingDate;

    boolean buildIsSupported = buildValueExtractor != null;
    final Locator timeLocator = buildIsSupported ?
                                new Locator(timeLocatorText, DATE, BUILD, CONDITION, INCLUDE_INITIAL, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME) :
                                new Locator(timeLocatorText, DATE, CONDITION, INCLUDE_INITIAL, Locator.LOCATOR_SINGLE_VALUE_UNUSED_NAME);
    timeLocator.addHiddenDimensions(SHIFT);
    final String time = timeLocator.getSingleValue();
    if (time != null) {
      limitingDate = TimeWithPrecision.parse(time, myTimeService);
    } else {
      final String shift = timeLocator.getSingleDimensionValue(SHIFT);
      final String dateDimension = timeLocator.getSingleDimensionValue(DATE);
      if (dateDimension != null) {
        limitingDate = TimeWithPrecision.parse(dateDimension, myTimeService);
      } else {
        if (buildIsSupported) {
          String build = timeLocator.getSingleDimensionValue(BUILD);
          if (build != null) {
            Date timeFromBuild = buildValueExtractor.get(getBuildPromotionFinder().getItem(build));
            if (timeFromBuild == null) {
              throw new BadRequestException("Cannot determine time from build found by locator '" + build + "'");
            }
            limitingDate = new TimeWithPrecision(timeFromBuild, false);
          } else if (shift != null) {
            limitingDate = new TimeWithPrecision(new Date(myTimeService.now()), false);
          } else {
            throw new BadRequestException("Invalid locator: should contain '" + DATE + "' or '" + BUILD + "' dimensions or be relative time offset starting with '-'.");
          }
        } else {
          throw new BadRequestException("Invalid locator: should contain '" + DATE + "' dimension or be relative time offset starting with '-'.");
        }
      }

      if (shift != null) {
        if (shift.startsWith("-")) {
          limitingDate = new TimeWithPrecision(new Date(limitingDate.getTime().getTime() - TimeWithPrecision.getMsFromRelativeTime(shift.substring("-".length()))),
                                               limitingDate.isSecondsPrecision());
        } else if (shift.startsWith("+")) {
          limitingDate = new TimeWithPrecision(new Date(limitingDate.getTime().getTime() + TimeWithPrecision.getMsFromRelativeTime(shift.substring("+".length()))),
                                               limitingDate.isSecondsPrecision());
        } else {
          throw new BadRequestException("Wrong value '" + shift + "' for '" + SHIFT + "' dimension: should start with '+' or '-'.");
        }
      }
    }

    final String conditionText = timeLocator.getSingleDimensionValue(CONDITION);
    final String conditionName = conditionText == null ? DATE_CONDITION_AFTER : conditionText; //todo: should it be "equal" instead???
    final Condition<Date> definedCondition = getCondition(conditionName);
    if (definedCondition == null) {
      throw new BadRequestException("Invalid condition name '" + conditionName + "'. Supported names are: " + Arrays.toString(getAllConditions()));
    }

    Boolean includeInitial = timeLocator.getSingleDimensionValueAsBoolean(INCLUDE_INITIAL, false);
    if (includeInitial == null) {
      includeInitial = false;
    }

    timeLocator.checkLocatorFullyProcessed();

    Condition<Date> resultingCondition;
    if (!includeInitial) {
      resultingCondition = definedCondition;
    } else {
      resultingCondition = new Condition<Date>() {
        @Override
        boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
          final boolean nestedResult = definedCondition.matches(refDate, tryDate);
          return refDate == null ? nestedResult : nestedResult || refDate.equals(tryDate);
        }
      };
    }

    if (limitingDate.isSecondsPrecision()) {
      final Condition<Date> currentCondition = resultingCondition;
      resultingCondition = new Condition<Date>() {
        @Override
        boolean matches(@Nullable final Date refDate, @NotNull final Date tryDate) {
          Calendar calendar = Calendar.getInstance();
          calendar.setTime(tryDate);
          calendar.set(Calendar.MILLISECOND, 0);

          return currentCondition.matches(refDate, calendar.getTime());
        }
      };
    }

    @Nullable TimeWithPrecision limitingSinceDate = DATE_CONDITION_AFTER.equals(conditionName) || DATE_CONDITION_EQUALS.equals(conditionName) ? limitingDate : null;
    return new ParsedTimeCondition(limitingSinceDate, limitingDate, resultingCondition);
  }

  public class ParsedTimeCondition implements Matcher<Date> {
    @Nullable private final TimeWithPrecision myLimitingSinceDate;
    @NotNull private final TimeWithPrecision myLimitingDate;
    @NotNull private final Condition<Date> myCondition;

    public ParsedTimeCondition(@Nullable final TimeWithPrecision limitingSinceDate,
                               @NotNull final TimeWithPrecision limitingDate,
                               @NotNull final Condition<Date> condition) {
      myLimitingSinceDate = limitingSinceDate;
      myLimitingDate = limitingDate;
      myCondition = condition;
    }

    @Override
    public boolean matches(@NotNull final Date date) {
      return myCondition.matches(myLimitingDate.getTime(), date);
    }

    @NotNull
    public Date getLimitingDate() {
      return myLimitingDate.getTime();
    }

    @Nullable
    public Date getLimitingSinceDate() {
      return myLimitingSinceDate == null ? null : myLimitingSinceDate.getTime();
    }
  }

  @Nullable
  private static Condition<Date> getCondition(@NotNull String name) {
    return ourTimeConditions.get(name);
  }

  @NotNull
  private static String[] getAllConditions() {
    return CollectionsUtil.toArray(ourTimeConditions.keySet(), String.class);
  }

  public static interface ValueExtractor<T, V> {
    @Nullable
    public V get(@NotNull T t);
  }

  abstract static class Condition<T> {
    abstract boolean matches(@Nullable final T refValue, @NotNull final T tryValue);
  }

  public static final ValueExtractor<BuildPromotion, Date> QUEUED_BUILD_TIME = new ValueExtractor<BuildPromotion, Date>() {
    @Nullable
    public Date get(@NotNull final BuildPromotion buildPromotion) {
      return buildPromotion.getQueuedDate();
    }
  };
  public static final ValueExtractor<BuildPromotion, Date> STARTED_BUILD_TIME = new ValueExtractor<BuildPromotion, Date>() {
    @Nullable
    public Date get(@NotNull final BuildPromotion buildPromotion) {
      final SBuild associatedBuild = buildPromotion.getAssociatedBuild();
      return associatedBuild == null ? null : associatedBuild.getStartDate();
    }
  };
  public static final ValueExtractor<BuildPromotion, Date> FINISHED_BUILD_TIME = new ValueExtractor<BuildPromotion, Date>() {
    @Nullable
    public Date get(@NotNull final BuildPromotion buildPromotion) {
      final SBuild associatedBuild = buildPromotion.getAssociatedBuild();
      return associatedBuild == null ? null : associatedBuild.getFinishDate();
    }
  };

  @Nullable
  public static Date maxDate(@Nullable final Date date1, @Nullable final Date date2) {
    if (date1 == null) return date2;
    if (date2 == null) return date1;
    if (Dates.isBeforeWithError(date1, date2, 0)) return date2;
    return date1;
  }

  static class FilterAndLimitingDate<T> {
    @NotNull private final FilterConditionChecker<T> filter;
    @Nullable private final Date limitingDate;

    public FilterAndLimitingDate(@NotNull final FilterConditionChecker<T> filter, @Nullable final Date limitingDate) {
      this.filter = filter;
      this.limitingDate = limitingDate;
    }

    @NotNull
    public FilterConditionChecker<T> getFilter() {
      return filter;
    }

    @Nullable
    public Date getLimitingDate() {
      return limitingDate;
    }
  }

  private static class AndedFilter<T> implements FilterConditionChecker<T> {
    @NotNull private final List<FilterConditionChecker<T>> myFilters = new ArrayList<>();

    public void add(@NotNull final FilterConditionChecker<T> filter) {
      myFilters.add(filter);
    }

    @Override
    public boolean isIncluded(@NotNull final T item) {
      for (FilterConditionChecker<T> filter : myFilters) {
        if (!filter.isIncluded(item)){
          return false;
        }
      }
      return true;
    }

  }
}
