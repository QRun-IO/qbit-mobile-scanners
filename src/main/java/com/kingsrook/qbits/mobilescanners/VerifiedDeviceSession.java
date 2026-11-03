/*
 * Copyright (c) 2026 Kingsrook, LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package com.kingsrook.qbits.mobilescanners;


import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.kingsrook.qqq.backend.core.model.session.QCredentialScope;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** Server-created per-request session; survives QQQ asynchronous context capture.
 ** No client process value or shared stored session can manufacture this type.
 *******************************************************************************/
final class VerifiedDeviceSession extends QSession
{
   private final String    deviceId;
   private final Authority checked;



   /***************************************************************************
    ** Immutable semantic copy of every session field that can grant or narrow
    ** access. QCredentialScope has no value equality, so its fields are copied.
    ***************************************************************************/
   private record Authority(String userId, String uuid, String channel, Set<String> permissions, Map<String, Set<Serializable>> securityKeys,
                            Map<String, Serializable> variants, Scope scope, String unverifiable) implements Serializable
   {
   }



   /***************************************************************************
    ** Credential ceilings and auth selectors (credential id, readOnly, channel).
    ***************************************************************************/
   private record Scope(String credentialId, boolean readOnly, Set<String> apps, Set<String> tables, Set<String> processes,
                        Set<String> reports, Set<String> widgets, Set<String> operationIds) implements Serializable
   {
   }



   /***************************************************************************
    ** Preserve all user restrictions while isolating mutable session state.
    ***************************************************************************/
   VerifiedDeviceSession(QSession original, String deviceId) throws CloneNotSupportedException
   {
      QSession copy = original.clone();
      this.deviceId = deviceId;
      setIdReference(copy.getIdReference());
      setUuid(copy.getUuid());
      setUser(copy.getUser());
      setPermissions(copy.getPermissions());
      setSecurityKeyValues(copy.getSecurityKeyValues());
      setBackendVariants(copy.getBackendVariants());
      setValues(copy.getValues());
      setValuesForFrontend(copy.getValuesForFrontend());
      setClientChannel(copy.getClientChannel());
      setCredentialScope(copy.getCredentialScope());
      this.checked = authority(this);
   }



   /***************************************************************************
    ** Identity verified from the HTTP credential, never process input.
    ***************************************************************************/
   String deviceId()
   {
      return deviceId;
   }



   /***************************************************************************
    ** The authority verified when this wrapper was created.
    ***************************************************************************/
   Serializable checkedAuthority()
   {
      return checked;
   }



   /***************************************************************************
    ** Current semantic authority of any session, for comparison with a prior one.
    ***************************************************************************/
   static Serializable authorityOf(QSession session)
   {
      return authority(session);
   }



   /***************************************************************************
    ** Copies the collections; QSession.clone and QCredentialScope.clone are the
    ** native isolation and copy elements shallowly. Element values that are not
    ** known immutable types could change snapshot and session together, so such
    ** a snapshot is made unequal to every other and the comparison fails closed.
    ***************************************************************************/
   private static Authority authority(QSession session)
   {
      String unverifiable = null;
      Map<String, Set<Serializable>> keys = null;
      if(session.getSecurityKeyValues() != null)
      {
         keys = new HashMap<>();
         for(Map.Entry<String, List<Serializable>> entry : session.getSecurityKeyValues().entrySet())
         {
            //////////////////////////////////////////////////////////////////////////////////
            // core row security tests List.contains: membership is authority, so order and //
            // repeated identical appends are not changes. A null list stays distinct.      //
            //////////////////////////////////////////////////////////////////////////////////
            Set<Serializable> values = entry.getValue() == null ? null : new HashSet<>(entry.getValue());
            if(values != null && !values.stream().allMatch(VerifiedDeviceSession::isImmutable))
            {
               unverifiable = UUID.randomUUID().toString();
            }
            keys.put(entry.getKey(), values);
         }
      }
      Map<String, Serializable> variants = session.getBackendVariants() == null ? null : new HashMap<>(session.getBackendVariants());
      if(variants != null && !variants.values().stream().allMatch(VerifiedDeviceSession::isImmutable))
      {
         unverifiable = UUID.randomUUID().toString();
      }
      QCredentialScope scope = session.getCredentialScope();
      return new Authority(session.getUser() == null ? null : session.getUser().getIdReference(), session.getUuid(), session.getClientChannel(),
         copy(session.getPermissions()), keys, variants,
         scope == null ? null : new Scope(scope.getCredentialId(), scope.getReadOnly(), copy(scope.getApps()), copy(scope.getTables()),
            copy(scope.getProcesses()), copy(scope.getReports()), copy(scope.getWidgets()), copy(scope.getOperationIds())), unverifiable);
   }



   /***************************************************************************
    ** Only value types that cannot be mutated in place.
    ***************************************************************************/
   private static boolean isImmutable(Serializable value)
   {
      return value == null || value instanceof String || value instanceof Integer || value instanceof Long || value instanceof Short
         || value instanceof Byte || value instanceof Double || value instanceof Float || value instanceof BigInteger
         || value instanceof BigDecimal || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>
         || value instanceof UUID || value instanceof Temporal;
   }



   /***************************************************************************
    ** Null means unrestricted, which must stay distinct from an empty set.
    ***************************************************************************/
   private static Set<String> copy(Set<String> set)
   {
      return set == null ? null : new HashSet<>(set);
   }
}
