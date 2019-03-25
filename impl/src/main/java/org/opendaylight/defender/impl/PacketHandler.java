/*
 * Copyright © 2017 zhiyifang and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.defender.impl;

import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.LowWaterMarkBreachedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.defenderplugin.rev180721.SampleDataLwm;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketReceived;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketHandler implements PacketProcessingListener{

	static Integer counter1 = 0;


	private DataBroker dataBroker;
	private NotificationPublishService notificationPublishService;

	public PacketHandler(DataBroker dataBroker, NotificationPublishService notificationPublishService) {
		this.dataBroker = dataBroker;
		this.notificationPublishService = notificationPublishService;
	}

	// packet in 计数器和packet in的大小
	int counter = 0, packetSize;

	// 平均Packet in速率
	float avgPacketInRate;

	// Calendar 实例
	Calendar calendar = Calendar.getInstance();

	// 开始时间
	Long oldTime = calendar.getTimeInMillis();

	// 截止时间和时间差
	Long newTime, timeDiff;

	// 低阈值
	int lowWaterMark = 1000;


	// 计算速率的单位值
	int samplesLwm = 1000;
	int samplesHwm = 2000;

	// 源目的IP和MAC地址还有IP协议
	String srcIP, dstIP, ipProtocol, srcMac, dstMac;

	// Ethernet类型
	String stringEthType;

	// TCP UDP 源目的端口号
	Integer srcPort, dstPort;

	// Reference to OpenFlow Plugin Yang DataStore
	NodeConnectorRef ingressNodeConnectorRef;
	// Ingress Switch Id
	NodeId ingressNodeId;
	// Ingress Switch Port Id from DataStore
	NodeConnectorId ingressNodeConnectorId;
	// Ingress Switch Port and Switch
	String ingressConnector, ingressNode;
	byte[] payload, srcMacRaw, dstMacRaw, srcIPRaw, dstIPRaw, rawIPProtocol, rawEthType, rawSrcPort, rawDstPort;
	DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	// Low Water Mark Breach - Upward and Downward Times
	String upwardTime, downwardTime, diffTimeString;
	// Time Interval Above LWM and Below LWM
	Long upTime, downTime, currentTime;
	// 是否到达警戒线
	Boolean lwmBreach = false;
	Boolean Data = false;
	// 是否发送了警告消息
	Boolean notificationSent = false;
	// 已经存储的警告的key值
	List<String> dataBaseKeyList = new ArrayList<String>();
	// 向datastore中存储警告消息的类
	DefenderStore DefenderStore = new DefenderStore(dataBroker);
	Double diffTime;

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(PacketHandler.class);

	PrintWriter restoreNo;

	public DataBroker getdataBroker() {
		return dataBroker;
	}

	public void setdataBroker(DataBroker dataBroker) {
		this.dataBroker = dataBroker;
	}

	public void init() {
		LOG.info("PacketHandler init");
	}

	public void close() {
		LOG.info("PacketHandler close");
	}

	public void setNotificationPublisService(NotificationPublishService notificationPublishService) {
		this.notificationPublishService = notificationPublishService;
	}

	public NotificationPublishService getNotificationPublisService(
			NotificationPublishService notificationPublishService) {
		return this.notificationPublishService;
	}



	public void Lwm() {

		// 阈值下限
		LOG.debug("Low Water Mark is " + lowWaterMark);

		// 计数器加1
		counter = counter + 1;
		// 计算平均packet in速率
		// 收到samplesLwm个数据包以后
		if ((counter % samplesLwm) == 0) {
			// 获取calendar
			calendar = Calendar.getInstance();
			// 获取当前时间
			newTime = calendar.getTimeInMillis();
			// 计算时间差
			timeDiff = newTime - oldTime;
			// 将oldTime时间更新
			oldTime = newTime;
			// 收到个数据包的平均速率，单位包/秒
			avgPacketInRate = (samplesLwm / timeDiff) * 1000;
			counter = 0;
			LOG.info("Average PacketIn Rate is " + avgPacketInRate);
		}

		// 如果平均包速率比lowWaterMark值大
		if (avgPacketInRate > lowWaterMark) {

			// lwmBreach的初始值是false
			if (lwmBreach.equals(false)) {
				LowWaterMarkBreachedBuilder lowWaterMarkBreachedBuilder = new LowWaterMarkBreachedBuilder();
				lowWaterMarkBreachedBuilder.setSrcPort(srcPort);
				lowWaterMarkBreachedBuilder.setDstPort(dstPort);
				lowWaterMarkBreachedBuilder.setSrcIP(srcIP);
				lowWaterMarkBreachedBuilder.setDstIP(dstIP);
				lowWaterMarkBreachedBuilder.setProtocol(ipProtocol);
				lowWaterMarkBreachedBuilder.setSrcMac(srcMac);
				lowWaterMarkBreachedBuilder.setDstMac(dstMac);
				LOG.debug("Put Notification");
				try {
					notificationPublishService.putNotification(lowWaterMarkBreachedBuilder.build());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			// 设置dataBroker
			DefenderStore.setdataBroker(dataBroker);
			calendar = Calendar.getInstance();
			downwardTime = "0";// 初始值设置为0，还不确定什么时候会降到警戒线以下
			// 已经达到警戒线，并已经发出通知
			lwmBreach = true;

			// 存到数据datastore中
			// 先设置key，用交换机和端口以及srcIP构成key
			String databaseKey = ingressNode + "-" + srcMac;
			// 避免重复存储
			if (!(dataBaseKeyList.contains(databaseKey))) {
				// 如果没有包含这个key就存到datastore中
				dataBaseKeyList.add(databaseKey);
				upTime = calendar.getTimeInMillis();
				upwardTime = dateFormat.format(upTime);
				DefenderStore.addData(databaseKey, ingressNode, ingressConnector, srcIP, dstIP,srcMac,dstMac, ipProtocol, srcPort,
						dstPort, packetSize, upwardTime, downwardTime);
			}
		}
		// 平均包速率比lowWaterMark小，并且之前达到了lowWaterMark
		else if ((avgPacketInRate < lowWaterMark) && lwmBreach) {
			// 低于警戒线
			lwmBreach = false;
			// 将低于lwmWaterMark的时间记录到数据库中
			for (String dbKey : dataBaseKeyList) {
				downTime = calendar.getTimeInMillis();
				downwardTime = dateFormat.format(downTime);// 重新设置downwardTime的值，即在这个时刻降到了警戒线以下
				// 将downtime添加到datastore中
				DefenderStore.addDownTime(dbKey, downwardTime);
			}
		}

	}

	@Override
	public void onPacketReceived(PacketReceived notification) {

		// 解析数据包
		ingressNodeConnectorRef = notification.getIngress();
		ingressNodeConnectorId = InventoryUtility.getNodeConnectorId(ingressNodeConnectorRef);
		ingressConnector = ingressNodeConnectorId.getValue();
		ingressNodeId = InventoryUtility.getNodeId(ingressNodeConnectorRef);
		ingressNode = ingressNodeId.getValue();

		// 从notification获取payload
		payload = notification.getPayload();

		// 获取payload的大小
		packetSize = payload.length;

		// 解析MAC地址
		srcMacRaw = PacketParsing.extractSrcMac(payload);
		dstMacRaw = PacketParsing.extractDstMac(payload);
		srcMac = PacketParsing.rawMacToString(srcMacRaw);
		dstMac = PacketParsing.rawMacToString(dstMacRaw);

		// 解析Ethernet类型
		rawEthType = PacketParsing.extractEtherType(payload);
		stringEthType = PacketParsing.rawEthTypeToString(rawEthType);

		// 解析IP地址
		dstIPRaw = PacketParsing.extractDstIP(payload);
		srcIPRaw = PacketParsing.extractSrcIP(payload);
		dstIP = PacketParsing.rawIPToString(dstIPRaw);
		srcIP = PacketParsing.rawIPToString(srcIPRaw);

		// 解析IP协议
		rawIPProtocol = PacketParsing.extractIPProtocol(payload);
		ipProtocol = PacketParsing.rawIPProtoToString(rawIPProtocol).toString();

		// 解析端口
		rawSrcPort = PacketParsing.extractSrcPort(payload);
		srcPort = PacketParsing.rawPortToInteger(rawSrcPort);
		rawDstPort = PacketParsing.extractDstPort(payload);
		dstPort = PacketParsing.rawPortToInteger(rawDstPort);

		Lwm();

	}
}
