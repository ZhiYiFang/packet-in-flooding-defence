/*
 * Copyright © 2017 zhiyifang and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.defender.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.DropActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.drop.action._case.DropActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.DefenderpluginListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.LowWaterMarkBreached;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowTableRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetDestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class HandlerModule implements DefenderpluginListener{
	Logger LOG = LoggerFactory.getLogger(HandlerModule.class);
	private DataBroker dataBroker;
	public static final String FLOW_ID_PREFIX = "USEC-";
	public static int flowNo = 0;
	private SalFlowService salFlowService;

	public HandlerModule(DataBroker dataBroker, SalFlowService salFlowService) {
		this.dataBroker = dataBroker;
		this.salFlowService = salFlowService;
	}

	@Override
	public void onLowWaterMarkBreached(LowWaterMarkBreached notification) {
		// 获取全部的节点
		List<Node> nodes = getAllNodes(dataBroker);
		String dstMAC = notification.getDstMac();
		if(!dstMAC.equals("FF:FF:FF:FF:FF:FF")) {
			// 如果是APR请求的话，则不按照这个目的地址添加drop流表，防止误杀
		// 遍历每一个节点，对每一个节点下发流表
		// 创建一个flow
			Flow flow = createProgibitFlow(notification);
			for (Node node : nodes) {
				NodeKey nodeKey = node.getKey();// 看Yang文件NodeKey的变量是NodeId
				// 寻找Nodes根节点下的子节点，由NodeKey来寻找Nodes下的子节点
				InstanceIdentifier<Node> nodeId = InstanceIdentifier.builder(Nodes.class).child(Node.class, nodeKey)
						.build();
				// 对每个节点下发流表
				addProhibitFlow(nodeId, flow);
			}
		}
	}

	private void addProhibitFlow(InstanceIdentifier<Node> nodeId,Flow flow) {
		// node 是遍历datastore中nodeids中的每一个nodeid
		LOG.info("Adding prohibit flows for node {} ", nodeId);
		// 根据nodeId获取tableId
		InstanceIdentifier<Table> tableId = getTableInstanceId(nodeId);

		// 创建一个FlowKey
		FlowKey flowKey = new FlowKey(new FlowId(FLOW_ID_PREFIX + String.valueOf(flowNo++)));

		// 在datastore中创建一个子路经
		InstanceIdentifier<Flow> flowId = tableId.child(Flow.class, flowKey);

		// 在这个子路经下添加一个流
		Future<RpcResult<AddFlowOutput>> result = writeFlow(nodeId, tableId, flowId,flow);
		AddFlowOutput output = null;
		try {
			output = result.get().getResult();
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		LOG.info("Added prohibit flows for node {} ", nodeId);
	}
	private Flow createProgibitFlow(LowWaterMarkBreached notification) {
		
		String srcMAC = notification.getSrcMac();
		String dstMAC = notification.getDstMac();
		// 设置名字和tableID以及flowID
		FlowBuilder builder = new FlowBuilder();
		builder.setFlowName("prohibitFlow").setTableId(Short.valueOf("0"));
		builder.setId(new FlowId(Long.toString(builder.hashCode())));
		// 设置匹配域
		MatchBuilder matchBuilder = new MatchBuilder();
		// 以太网的匹配
		EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder();
		// 以太网的目的地址
		EthernetDestinationBuilder ethernetDestinationBuilder = new EthernetDestinationBuilder();
		ethernetDestinationBuilder.setAddress(new MacAddress(dstMAC));
		ethernetMatchBuilder.setEthernetDestination(ethernetDestinationBuilder.build());
		EthernetSourceBuilder ethernetSourceBuilder = new EthernetSourceBuilder();
		ethernetSourceBuilder.setAddress(new MacAddress(srcMAC));
		ethernetMatchBuilder.setEthernetSource(ethernetSourceBuilder.build());
		matchBuilder.setEthernetMatch(ethernetMatchBuilder.build());
		// 设置匹配域
		builder.setMatch(matchBuilder.build());
		
		// 设置指令
		InstructionsBuilder instructionsBuilder = new InstructionsBuilder();
		InstructionBuilder instructionBuilder = new InstructionBuilder();
		ApplyActionsCaseBuilder actionsCaseBuilder = new ApplyActionsCaseBuilder();
        ApplyActionsBuilder actionsBuilder = new ApplyActionsBuilder();
		ActionBuilder actionBuilder = new ActionBuilder();
		actionBuilder.setAction(new DropActionCaseBuilder().setDropAction(new DropActionBuilder().build()).build());
		List<org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action> action = new ArrayList<>();
		action.add(actionBuilder.build());
		actionsBuilder.setAction(action);
		actionsCaseBuilder.setApplyActions(actionsBuilder.build());
		instructionBuilder.setInstruction(actionsCaseBuilder.build());
		List<Instruction> instructions = new ArrayList<>();
		instructions.add(instructionBuilder.build());
		instructionsBuilder.setInstruction(instructions);
		// 设置指令
		builder.setInstructions(instructionsBuilder.build());
		// 设置其他项
		builder.setPriority(50);
		builder.setHardTimeout(9999);
		builder.setIdleTimeout(9999);
		return builder.build();
	}
	private Future<RpcResult<AddFlowOutput>> writeFlow(InstanceIdentifier<Node> nodeInstanceId,
			InstanceIdentifier<Table> tableInstanceId, InstanceIdentifier<Flow> flowPath, Flow flow) {
		
		// 创建一个AddflowInputBuilder
		AddFlowInputBuilder builder = new AddFlowInputBuilder(flow);
		// 指定一个节点
		builder.setNode(new NodeRef(nodeInstanceId));
		// flow的路径
		builder.setFlowRef(new FlowRef(flowPath));
		// table的路径
		builder.setFlowTable(new FlowTableRef(tableInstanceId));
		builder.setTransactionUri(new Uri(flow.getId().getValue()));
		return salFlowService.addFlow(builder.build());
	}
	
	


	/**
	 * 根据nodeid获取tableId
	 * 
	 * @param nodeId
	 * @return
	 */
	private InstanceIdentifier<Table> getTableInstanceId(InstanceIdentifier<Node> nodeId) {

		// get flow table key
		// 获取0号流表
		short tableId = 0;
		TableKey flowTableKey = new TableKey(tableId);
		return nodeId.augmentation(FlowCapableNode.class).child(Table.class, flowTableKey);
	}

	/**
	 * 读取inventory数据库获取所有的节点
	 * 
	 * @param dataBroker
	 * @return
	 */
	private List<Node> getAllNodes(DataBroker dataBroker) {

		// 读取inventory数据库
		InstanceIdentifier.InstanceIdentifierBuilder<Nodes> nodesInsIdBuilder = InstanceIdentifier
				.<Nodes>builder(Nodes.class);
		// 两种构建instanceIdentifier的方式
		// InstanceIdentifier<Nodes> nodesInsIdBuilder = InstanceIdentifier.
		// create(Nodes.class);

		// 所有节点信息
		Nodes nodes = null;
		// 创建读事务
		try (ReadOnlyTransaction readOnlyTransaction = dataBroker.newReadOnlyTransaction()) {

			Optional<Nodes> dataObjectOptional = readOnlyTransaction
					.read(LogicalDatastoreType.OPERATIONAL, nodesInsIdBuilder.build()).get();
			// 如果数据不为空，获取到nodes
			if (dataObjectOptional.isPresent()) {
				nodes = dataObjectOptional.get();
			}
		} catch (InterruptedException e) {
			LOG.error("Failed to read nodes from Operation data store.");
			throw new RuntimeException("Failed to read nodes from Operation data store.", e);
		} catch (ExecutionException e) {
			LOG.error("Failed to read nodes from Operation data store.");
			throw new RuntimeException("Failed to read nodes from Operation data store.", e);
		}

		return nodes.getNode();
	}
}
