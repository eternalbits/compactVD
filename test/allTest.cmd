@echo off

call oneTest.cmd 3hfs.vdi   VDI  E17E0B405B3A42E8DFAF97AF116D792B F0710009C9E347D2BBF36A65975D062A
call oneTest.cmd 3ext.vmdk  VMDK 1D1E035F342383D8167ADE22E6B6E6DA 57335C94DBC1FAD7B1F322645D4115DE
call oneTest.cmd 3ext.vdi   VDI  4768E137452A32C00AE89FBF661529D9 B7A88EE6384FB6064CBADC2509FED56B
call oneTest.cmd 3ext.vhd   VHD  796D0009AB0E5FC30C8B25C966DE2564 A1110CC0800AA9C0E0D6FEC5445B850A
call oneTest.cmd 3lvm.vdi   VDI  009D8EF0F43CF3342ED1E6785DFEFFE2 D5A26F0970F474BE2ECB3318282F21D0
call oneTest.cmd 3ntfs.vdi  VDI  5C175B31F94504267B4BE2A943F77E63 D39C1833F500455D76A71AF7F816422A
call oneTest.cmd 3ntfs.vmdk VMDK 937805D77D9C9692E4E8110295E0FFD3 D686E7C0485B4475294C52C95C83A654
call oneTest.cmd 3hfs.vhd   VHD  E4D0A3A2F477D1484F55A360BE021310 5FB0D2AA57B5EF6A02909D64B2826351
pause
