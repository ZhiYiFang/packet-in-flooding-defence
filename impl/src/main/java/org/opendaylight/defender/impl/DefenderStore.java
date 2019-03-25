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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.LWM;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.LWMBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.lwm.Lowwatermark;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.lwm.LowwatermarkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.lwm.LowwatermarkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class DefenderStore {
	private DataBroker dataBroker;

	public void setdataBroker(DataBroker dataBroker) {
		this.dataBroker = dataBroker;
	}

	public DefenderStore(DataBroker dataBroker) {
		this.dataBroker = dataBroker;
	}

	// 设置往datastore中存储的根路径
	InstanceIdentifier<LWM> instanceIdentifier = InstanceIdentifier.builder(LWM.class).build();
	List<Lowwatermark> lwmList = new ArrayList<>();
	LWMBuilder lwmBuilder = new LWMBuilder();

	/**
	 * 想datastore中添加警告消息
	 * 
	 * @param secKey
	 * @param nodeId
	 * @param nodeConnectorId
	 * @param srcIP
	 * @param dstIP
	 * @param protocol
	 * @param srcPort
	 * @param dstPort
	 * @param packetSize
	 * @param uptime
	 * @param downtime
	 */
	public void addData(String secKey, String nodeId, String nodeConnectorId, String srcIP, String dstIP,String srcMAC, String dstMAC,
			String protocol, int srcPort, int dstPort, int packetSize, String uptime, String downtime) {
		WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
		LowwatermarkBuilder lowWaterMarkBuilder = new LowwatermarkBuilder();
		// key值是ingressNode + "-" + srcMac
		lowWaterMarkBuilder.setSecKey(secKey);
		lowWaterMarkBuilder.setNodeID(nodeId);
		lowWaterMarkBuilder.setSrcIP(srcIP);
		lowWaterMarkBuilder.setDstIP(dstIP);
		lowWaterMarkBuilder.setProtocol(protocol);
		lowWaterMarkBuilder.setSrcPort(srcPort);
		lowWaterMarkBuilder.setDstPort(dstPort);
		lowWaterMarkBuilder.setPacketSize(packetSize);
		lowWaterMarkBuilder.setUpwardTime(uptime);
		lowWaterMarkBuilder.setDownwardTime(downtime);
		lowWaterMarkBuilder.setSrcMAC(srcMAC);
		lowWaterMarkBuilder.setDstMAC(dstMAC);
		Lowwatermark lwm = lowWaterMarkBuilder.build();
		// 先向list中添加
		lwmList.add(lwm);
		// 将list赋值给LWM
		lwmBuilder.setLowwatermark(lwmList);
		LWM lwmsec = lwmBuilder.build();
		// 用merge操作来向根节点下添加子节点
		writeTransaction.merge(LogicalDatastoreType.OPERATIONAL, instanceIdentifier, lwmsec);
		writeTransaction.submit();
	}

	/**
	 * 更新downtime时间
	 * 
	 * @param secKey
	 * @param downtime
	 */
	public void addDownTime(String secKey, String downtime) {
		// 用ingressNode + "-" + srcMac 创建一个LowwatermarkKey
		LowwatermarkKey seclwmKey = new LowwatermarkKey(secKey);
		// 从LWM的子节点LowwatermarkKey下寻找相应的记录
		InstanceIdentifier<Lowwatermark> secLwmId = InstanceIdentifier.builder(LWM.class)
				.child(Lowwatermark.class, seclwmKey).build();
		WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
		LowwatermarkBuilder lowWaterMarkBuilder = new LowwatermarkBuilder();
		lowWaterMarkBuilder.setSecKey(secKey);
		lowWaterMarkBuilder.setDownwardTime(downtime);
		Lowwatermark lwmElements = lowWaterMarkBuilder.build();
		lwmList.add(lwmElements);
		// 用merge操作来更新downtime
		writeTransaction.merge(LogicalDatastoreType.OPERATIONAL, secLwmId, lwmElements);
		writeTransaction.submit();
	}
}
