package com.autoproduction.mvp.module.dispatch.controller;

import com.autoproduction.mvp.api.ApiSupport;
import com.autoproduction.mvp.api.ContractControllerSupport;
import com.autoproduction.mvp.module.orderexecution.OrderExecutionFacade;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(InternalContractController.BASE_PATH)
public class InternalOrderExecutionContractController {
  private final OrderExecutionFacade orderExecutionFacade;

  public InternalOrderExecutionContractController(OrderExecutionFacade orderExecutionFacade) {
    this.orderExecutionFacade = orderExecutionFacade;
  }

  @GetMapping("/order-pool")
  public ResponseEntity<Map<String, Object>> listOrderPool(HttpServletRequest request, @RequestParam Map<String, String> filters) {
    return ContractControllerSupport.listResponse(request, orderExecutionFacade.listOrderPool(filters));
  }

  @GetMapping("/order-pool/{orderNo}/materials")
  public ResponseEntity<Map<String, Object>> listOrderPoolMaterials(
    HttpServletRequest request,
    @PathVariable("orderNo") String orderNo,
    @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh
  ) {
    return ContractControllerSupport.listResponse(request, orderExecutionFacade.listOrderPoolMaterials(orderNo, refresh));
  }

  @GetMapping("/order-pool/materials/{parentMaterialCode}/children")
  public ResponseEntity<Map<String, Object>> listMaterialChildrenByParentCode(
    HttpServletRequest request,
    @PathVariable("parentMaterialCode") String parentMaterialCode,
    @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh
  ) {
    return ContractControllerSupport.listResponse(
      request,
      orderExecutionFacade.listMaterialChildrenByParentCode(parentMaterialCode, refresh)
    );
  }

  @GetMapping("/material-availability/orders")
  public ResponseEntity<Map<String, Object>> listOrderMaterialAvailability(
    HttpServletRequest request,
    @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh
  ) {
    return ContractControllerSupport.listResponse(request, orderExecutionFacade.listOrderMaterialAvailability(refresh));
  }

  @PostMapping("/order-pool/{orderNo}/patch")
  public ResponseEntity<Map<String, Object>> patchOrderPoolOrder(
    HttpServletRequest request,
    @PathVariable("orderNo") String orderNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "order-pool-admin");
    return ApiSupport.ok(requestId, orderExecutionFacade.patchOrder(orderNo, body, requestId, operator));
  }

  @PostMapping("/order-pool/{orderNo}/delete")
  public ResponseEntity<Map<String, Object>> deleteOrderPoolOrder(
    HttpServletRequest request,
    @PathVariable("orderNo") String orderNo,
    @RequestBody(required = false) Map<String, Object> payload
  ) {
    Map<String, Object> body = InternalContractRequestSupport.body(payload);
    String requestId = InternalContractRequestSupport.requireRequestId(request, body);
    String operator = InternalContractRequestSupport.operator(body, "order-pool-admin");
    return ApiSupport.ok(requestId, orderExecutionFacade.deleteOrder(orderNo, requestId, operator));
  }
}
