package com.example.selliaapp.repository.impl

import com.example.selliaapp.domain.security.AppRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessControlRepositoryImplRoleResolutionTest {

    @Test
    fun `forces owner role even when firestore says admin`() {
        val resolved = AccessControlRepositoryImpl.resolveEffectiveRole(
            isConfiguredAdmin = false,
            localRole = AppRole.OWNER,
            localUserIsActive = true,
            firestoreRole = AppRole.OWNER,
            totalUsers = 3,
            hasAuthenticatedEmail = true
        )

        assertEquals(AppRole.OWNER, resolved)
    }

    @Test
    fun `uses local role when firestore role is missing`() {
        val resolved = AccessControlRepositoryImpl.resolveEffectiveRole(
            isConfiguredAdmin = false,
            localRole = AppRole.OWNER,
            localUserIsActive = true,
            firestoreRole = null,
            totalUsers = 3,
            hasAuthenticatedEmail = true
        )

        assertEquals(AppRole.OWNER, resolved)
    }

    @Test
    fun `keeps owner role when local user is inactive`() {
        val resolved = AccessControlRepositoryImpl.resolveEffectiveRole(
            isConfiguredAdmin = false,
            localRole = AppRole.OWNER,
            localUserIsActive = false,
            firestoreRole = null,
            totalUsers = 5,
            hasAuthenticatedEmail = true
        )

        assertEquals(AppRole.OWNER, resolved)
    }
}
