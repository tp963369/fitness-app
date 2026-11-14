package com.example

import com.example.ui.viewmodel.AdminScreen
import com.example.ui.viewmodel.GymPlatformViewModel
import com.example.ui.viewmodel.MemberScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleRobolectricTest {

  @Test
  fun `verify 37 member screens and 32 admin screens and viewModel navigation`() {
    assertEquals(37, MemberScreen.entries.size)
    assertEquals(32, AdminScreen.entries.size)

    val vm = GymPlatformViewModel()
    assertEquals("IronPulse Flagship Fitness", vm.state.value.settings.gymName)

    vm.navigateMember(MemberScreen.QR_SCANNER)
    assertEquals(MemberScreen.QR_SCANNER, vm.currentMemberScreen.value)

    vm.navigateAdmin(AdminScreen.PAYMENT_APPROVAL_CENTRE)
    assertEquals(AdminScreen.PAYMENT_APPROVAL_CENTRE, vm.currentAdminScreen.value)
  }
}
