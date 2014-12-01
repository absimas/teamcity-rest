/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package jetbrains.buildServer.server.rest.data.build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import jetbrains.buildServer.server.rest.data.*;
import jetbrains.buildServer.server.rest.errors.BadRequestException;
import jetbrains.buildServer.server.rest.model.PagerData;
import jetbrains.buildServer.serverSide.BuildPromotion;
import jetbrains.buildServer.serverSide.TagData;
import jetbrains.buildServer.users.SUser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Yegor.Yarko
 *         Date: 26.11.2014
 */
public class TagFinder extends AbstractFinder<TagData> {

  public static final String NAME = "name";
  public static final String PRIVATE = "private";
  public static final String OWNER = "owner";

  @NotNull private final UserFinder myUserFinder;
  @NotNull private final BuildPromotion myBuildPromotion;

  public TagFinder(final @NotNull UserFinder userFinder, final @NotNull BuildPromotion buildPromotion) {
    super(new String[]{DIMENSION_ID, NAME, PRIVATE, OWNER});
    myUserFinder = userFinder;
    myBuildPromotion = buildPromotion;
  }

  public static Locator getDefaultLocator(){
    Locator defaultLocator = Locator.createEmptyLocator();
    defaultLocator.setDimension(TagFinder.PRIVATE, "false");
    return defaultLocator;
  }

  @Override
  @NotNull
  public Locator createLocator(@Nullable final String locatorText, @Nullable final Locator locatorDefaults) {
    final Locator locator = super.createLocator(locatorText, locatorDefaults);
    locator.addHiddenDimensions(PagerData.START, PagerData.COUNT);
    return locator;
  }

  @NotNull
  @Override
  public List<TagData> getAllItems() {
    final ArrayList<TagData> result = new ArrayList<TagData>(myBuildPromotion.getTagDatas());
    Collections.sort(result, new Comparator<TagData>() {
      public int compare(final TagData o1, final TagData o2) {
        if (o1 == o2) return 0;
        if (o1 == null) return -1;
        if (o2 == null) return 1;

        if (o1.isPublic()){
          if (o2.isPublic()){
            return o1.getLabel().compareToIgnoreCase(o2.getLabel());
          }
          return -1;
        }
        if (o2.isPublic()){
          return 1;
        }
        final SUser user1 = o1.getOwner();
        final SUser user2 = o2.getOwner();
        if (user1 == user2 || user1 == null || user2 == null) return o1.getLabel().compareToIgnoreCase(o2.getLabel());
        return user1.getUsername().compareToIgnoreCase(user2.getUsername());
      }
    });
    return result;
  }

  @NotNull
  @Override
  protected AbstractFilter<TagData> getFilter(final Locator locator) {
    if (locator.isSingleValue()) {
      throw new BadRequestException("Single value locator '" + locator.getSingleValue() + "' is not supported for several items query.");
    }

    final Long countFromFilter = locator.getSingleDimensionValueAsLong(PagerData.COUNT);
    final MultiCheckerFilter<TagData> result =
      new MultiCheckerFilter<TagData>(locator.getSingleDimensionValueAsLong(PagerData.START), countFromFilter != null ? countFromFilter.intValue() : null, null);

    final String nameDimension = locator.getSingleDimensionValue(NAME);
    if (nameDimension != null) {
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          return nameDimension.equalsIgnoreCase(item.getLabel());
        }
      });
    }

    final Boolean privateDimension = locator.getSingleDimensionValueAsBoolean(PRIVATE);
    if (privateDimension != null) {
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          return FilterUtil.isIncludedByBooleanFilter(privateDimension, item.getOwner() != null);
        }
      });
    }

    final String ownerLocator = locator.getSingleDimensionValue(OWNER);
    if (ownerLocator != null) {
      final SUser user = myUserFinder.getUser(ownerLocator);
      result.add(new FilterConditionChecker<TagData>() {
        public boolean isIncluded(@NotNull final TagData item) {
          final SUser owner = item.getOwner();
          if (privateDimension == null && owner == null) {
            //locator "private:any,owner:<user>" should return all public and private of the user (the defaults)
            return true;
          }
          return user.equals(owner);
        }
      });
    }

    return result;
  }
}