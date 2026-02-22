package com.example.selliaapp.repository.impl

import com.example.selliaapp.domain.security.AppRole
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessControlRepositoryImplRoleResolutionTest {

    @Test
    fun `uses firestore role over local role when both exist`() {
        val resolved = AccessControlRepositoryImpl.resolveEffectiveRole(
            isConfiguredAdmin = false,
            localRole = AppRole.OWNER,
            localUserIsActive = true,
            firestoreRole = AppRole.ADMIN,
            totalUsers = 3,
            hasAuthenticatedEmail = true
        )

        assertEquals(AppRole.ADMIN, resolved)
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
    fun `keeps admin active by default even if local user is inactive`() {
        val resolved = AccessControlRepositoryImpl.resolveEffectiveRole(
            isConfiguredAdmin = false,
            localRole = AppRole.ADMIN,
            localUserIsActive = false,
            firestoreRole = null,
            totalUsers = 5,
            hasAuthenticatedEmail = true
        )

        assertEquals(AppRole.ADMIN, resolved)
    }
}
