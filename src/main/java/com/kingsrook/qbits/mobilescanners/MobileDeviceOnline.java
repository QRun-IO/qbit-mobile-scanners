/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.customizers.AbstractPostQueryCustomizer;
import com.kingsrook.qqq.backend.core.actions.tables.query.CriteriaExpansionContext;
import com.kingsrook.qqq.backend.core.actions.tables.query.SearchTermKind;
import com.kingsrook.qqq.backend.core.actions.tables.query.VirtualFieldCriteriaExpanderInterface;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Native virtual online field: no stale stored boolean or reconciliation race.
 *******************************************************************************/
public class MobileDeviceOnline extends AbstractPostQueryCustomizer implements VirtualFieldCriteriaExpanderInterface
{
   /***************************************************************************
    ** RC.20 has no virtual dependency hook; include the safe source field before SQL.
    ***************************************************************************/
   static void includeProjectionDependency(QueryInput query)
   {
      if(query.getFieldNamesToInclude() != null && query.getFieldNamesToInclude().contains("online"))
      {
         var fields = new HashSet<>(query.getFieldNamesToInclude());
         fields.add("lastSeenAt");
         query.setFieldNamesToInclude(fields);
      }
   }

   /***************************************************************************
    ** Every read derives presence from the last authenticated server receipt.
    ***************************************************************************/
   @Override
   public List<QRecord> apply(List<QRecord> records)
   {
      Instant cutoff = Instant.now().minusSeconds(15);
      for(QRecord record : records)
      {
         Instant seen = record.getValueInstant("lastSeenAt");
         record.setValue("online", seen != null && !seen.isBefore(cutoff));
      }
      return records;
   }

   /***************************************************************************
    ** Database query/count selection uses exactly the same last-seen boundary.
    ***************************************************************************/
   @Override
   public QQueryFilter expand(QTableMetaData table, QFilterCriteria criteria, CriteriaExpansionContext context) throws QException
   {
      boolean online;
      if(criteria.getOperator() == QCriteriaOperator.TRUE || criteria.getOperator() == QCriteriaOperator.FALSE)
      {
         online = criteria.getOperator() == QCriteriaOperator.TRUE;
      }
      else if((criteria.getOperator() == QCriteriaOperator.EQUALS || criteria.getOperator() == QCriteriaOperator.NOT_EQUALS)
         && criteria.getValues() != null && criteria.getValues().size() == 1
         && List.of("true", "false").contains(String.valueOf(criteria.getValues().getFirst())))
      {
         online = Boolean.parseBoolean(String.valueOf(criteria.getValues().getFirst()));
         if(criteria.getOperator() == QCriteriaOperator.NOT_EQUALS)
         {
            online = !online;
         }
      }
      else
      {
         throw new QBadRequestException("Online supports true/false and boolean equals/not-equals filters.");
      }
      Instant cutoff = Instant.now().minusSeconds(15);
      if(online)
      {
         return new QQueryFilter().withCriteria("lastSeenAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, cutoff);
      }
      return new QQueryFilter().withBooleanOperator(QQueryFilter.BooleanOperator.OR)
         .withCriteria("lastSeenAt", QCriteriaOperator.LESS_THAN, cutoff).withCriteria("lastSeenAt", QCriteriaOperator.IS_BLANK);
   }

   /***************************************************************************
    ** Free-text search does not infer a device connectivity filter.
    ***************************************************************************/
   @Override
   public QQueryFilter expandSearchTerm(QTableMetaData table, String term, SearchTermKind kind, CriteriaExpansionContext context)
   {
      return null;
   }
}
