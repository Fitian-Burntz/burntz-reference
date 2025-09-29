package com.seocheon.fitian.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.seocheon.fitian.auth.annotation.AllowedRanks;
import com.seocheon.fitian.auth.annotation.CurrentUser;
import com.seocheon.fitian.auth.security.CustomUserDetails;
import com.seocheon.fitian.dto.MemberResponseDto;
import com.seocheon.fitian.dto.MembersResponseDto;
import com.seocheon.fitian.dto.RankUpdateRequest;
import com.seocheon.fitian.dto.UpdateSelfRequest;
import com.seocheon.fitian.dto.channel.ChannelListResponseDto;
import com.seocheon.fitian.model.ApiResponse;
import com.seocheon.fitian.model.MemberModel;
import com.seocheon.fitian.service.ChannelService;
import com.seocheon.fitian.service.MemberService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name="Member API", description="회원 관련 기능 API")
@RestController
@RequestMapping("/member")
@SecurityRequirement(name="bearerAuth")
@CrossOrigin(origins = "http://localhost:8088")
public class MemberController {

	@Autowired
	private MemberService memberService;
	
	@Autowired
	private ChannelService channelService;
	
	//본인 정보 가져오기
	@Operation(summary = "현재 로그인한 사용자 정보", description = "accessToken 기반으로 로그인한 회원의 정보를 반환합니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@GetMapping("/me")
    public ResponseEntity<ApiResponse<MemberResponseDto>> getMyInfo(
    		@CurrentUser CustomUserDetails userDetails) {
		
		MemberModel me = memberService.findByUid(userDetails.getUsername());
		
    	return ResponseEntity.ok(ApiResponse.success(MemberResponseDto.from(me)));
    }
	
	//본인 정보 바꾸기
	@Operation(summary = "본인 정보 수정", description = "로그인한 회원이 자신의 name, email, 등록 박스 등을 수정합니다.",
			security = {})
	@PutMapping("/me")
	public ResponseEntity<ApiResponse<MemberResponseDto>> updateMyInfo(
			@RequestBody UpdateSelfRequest req,
			@CurrentUser CustomUserDetails userDetails) {
		
		if(userDetails.getUsername() == null || userDetails.getUsername().trim().isEmpty()) {
			return ResponseEntity.badRequest().body(ApiResponse.failure("uid가 누락되었습니다."));
		}
		
		MemberResponseDto dto =  memberService.updateMyInfo(req, userDetails);
	    return ResponseEntity.ok(ApiResponse.success(dto, "내 정보가 수정되었습니다."));
	}
	
	//관리자의 다른 회원 정보 수정
	@Operation(summary = "회원 등급 변경", description = "관리자가 회원의 등급을 guest/member/manager 중 하나로 변경합니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@AllowedRanks({"owner","manager"})
	@PutMapping("/admin/updateMember")
    public ResponseEntity<ApiResponse<MemberResponseDto>> updateMember(
    		@RequestBody RankUpdateRequest update,
    		@CurrentUser CustomUserDetails userDetails) {

		//타겟 회원이 owner 일시 못바꿈
		if(member.getRank().equals("owner")) {
			return ResponseEntity.badRequest().body(ApiResponse.failure("바꿀 수 없는 회원입니다."));
		}
		
		//본인과 다른 박스코드의 회원은 못바꿈
		if(!member.getBoxCode().equals(userDetails.getMember().getBoxCode())) {
			return ResponseEntity.badRequest().body(ApiResponse.failure("다른 박스의 회원 정보는 변경할 수 없습니다."));
		}
		
		MemberModel member = memberService.findByUid(update.getUid());
		String oldRank = member.getRank();
		String newRank = update.getRank();
		
		if(oldRank.equals("guest")) {
			//public인 채널에 전부 참여자로 추가
			List<ChannelListResponseDto> channelListPublic = channelService.getChannelListByType(member.getBoxCode(), "public");
			for(ChannelListResponseDto dto : channelListPublic) {
				channelService.inviteParticipant(member.getBoxCode(), dto.getChannelId(), member.getUid(), userDetails.getUsername());
			}
			//notice인 채널에 전부 참여자로 추가
			List<ChannelListResponseDto> channelListNotice = channelService.getChannelListByType(member.getBoxCode(), "notice");
			for(ChannelListResponseDto dto : channelListNotice) {
				channelService.inviteParticipant(member.getBoxCode(), dto.getChannelId(), member.getUid(), userDetails.getUsername());
			}
			//general인 채널에 전부 참여자로 추가
			List<ChannelListResponseDto> channelListGeneral = channelService.getChannelListByType(member.getBoxCode(), "general");
			for(ChannelListResponseDto dto : channelListGeneral) {
				channelService.inviteParticipant(member.getBoxCode(), dto.getChannelId(), member.getUid(), userDetails.getUsername());
			}
		} else if (newRank.equals("guest")) {
			List<ChannelListResponseDto> channelList = channelService.getChannelList(member.getBoxCode(), member.getUid());
			for(ChannelListResponseDto dto : channelList) {
				channelService.deleteParticipant(member.getBoxCode(), dto.getChannelId(), member.getUid());
			}
		}
		
		member.setRank(update.getRank());
		
		MemberResponseDto dto = memberService.updateMember(member);
		
    	return ResponseEntity.ok(ApiResponse.success(dto, "회원 정보가 수정되었습니다."));
    }
	
	//owner 양도
	@Operation(summary = "owner 양도", description = "owner가 manager에게 owner를 양도하고 manager가 됩니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@AllowedRanks({"owner"})
	@PutMapping("/admin/changeOwner")
    public ResponseEntity<ApiResponse<MemberResponseDto>> changeOwner(
    		@Parameter(description="owner를 받을 uid", required = true)
    		@RequestParam("uid") String uid,
    		@CurrentUser CustomUserDetails userDetails) {
		MemberModel member = memberService.findByUid(uid);
		
		if(!member.getRank().equals("manager")) {
			return ResponseEntity.badRequest().body(ApiResponse.failure("매니저에게만 양도 가능합니다."));
		}
		
		MemberResponseDto dto = memberService.changeOwner(member, userDetails);
		
    	return ResponseEntity.ok(ApiResponse.success(dto, "내 정보가 수정되었습니다."));
    }
	
	@Operation(summary = "특정 회원 정보 반환", description ="member/owner/manager 가 회원의 uid로 그 회원정보를 가져옵니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@AllowedRanks({"member","owner","manager"})
	@GetMapping("/getMember")
    public ResponseEntity<ApiResponse<MemberResponseDto>> getMember(
    		@Parameter(description="정보 반환받고 싶은 회원의 uid", required = true)
    		@RequestParam("uid") String uid,
    		@CurrentUser CustomUserDetails userDetails) {
    	MemberModel m = memberService.findByUid(uid);
    	
    	if(!m.getBoxCode().equals(userDetails.getMember().getBoxCode())) {
			return ResponseEntity.badRequest().body(ApiResponse.failure("다른 박스의 회원 정보는 열람할 수 없습니다."));
		}
		
    	return ResponseEntity.ok(ApiResponse.success(MemberResponseDto.from(m)));
    }
	
	@Operation(summary = "전체 멤버 반환", description ="owner/manager 가 boxCode로 해당 박스의 전체 회원 정보를 가져옵니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@AllowedRanks({"owner","manager"})
	@GetMapping("/getAllMember")
	public ResponseEntity<ApiResponse<MembersResponseDto>> getAllMember(
    		@CurrentUser CustomUserDetails userDetails) {
    	
    	String boxCode = userDetails.getMember().getBoxCode();
    	
    	MemberModel req = new MemberModel();
    	req.setBoxCode(boxCode);
    	
    	MembersResponseDto dto = memberService.getAllMember(req);
		
    	return ResponseEntity.ok(ApiResponse.success(dto));
    }
	
	@Operation(summary = "회원 탈퇴", description ="본인이 탈퇴를 요청하여 useYN 값을 N으로 변경합니다.",
			security = @SecurityRequirement(name = "bearerAuth"))
	@PutMapping("/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(
    		@CurrentUser CustomUserDetails userDetails) {
		
		memberService.deactivate(userDetails.getUsername());
    	return ResponseEntity.ok(ApiResponse.success(null, "정상적으로 탈퇴되었습니다."));
    }
	
}
