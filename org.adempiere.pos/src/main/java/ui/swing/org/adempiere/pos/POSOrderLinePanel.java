/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 Adempiere, Inc. All Rights Reserved.               *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *****************************************************************************/

package org.adempiere.pos;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.math.BigDecimal;

import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;

import org.adempiere.pos.service.I_POSPanel;
import org.compiere.minigrid.IDColumn;
import org.compiere.model.PO;
import org.compiere.swing.CScrollPane;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;

/**
 * Current Line Sub Panel
 * 
 * @author OpenXpertya
 * Based on Modified Original Code, Revised and Optimized
 *         *Copyright Jorg Janke
 * red1 - [2093355 ] Small bugs in OpenXpertya POS
 *  @author Susanne Calderón Schöningh, Systemhaus Westfalia
 *  @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
 *  <li> Implement best practices
 *  
 *  @version $Id: QueryProduct.java,v 1.1 jjanke Exp $
 *  @version $Id: QueryProduct.java,v 2.0 2015/09/01 00:00:00 scalderon
 *  
 */
public class POSOrderLinePanel extends POSSubPanel 
	implements ActionListener, FocusListener, 
		ListSelectionListener, TableModelListener, I_POSPanel, KeyListener, MouseListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -4023538043556457231L;

	/**
	 * Constructor
	 * 
	 * @param posPanel POS Panel
	 */
	public POSOrderLinePanel(VPOS posPanel) {
		super(posPanel);
	}

	/**	Current Order Line	*/
	private int orderLineId = 0;
	
	/**	Logger				*/
	private static CLogger 	log = CLogger.getCLogger(POSOrderLinePanel.class);
	

	/** The Table			*/
	private POSTable 		posTable;
	/**	Table Handle		*/
	private POSOrderLineTableHandle orderLineTableHandle;
	
	/**
	 * Get Table Width
	 * @return
	 * @return int
	 */
	public int getTableWidth() { 
		return posTable.getColumnModel().getTotalColumnWidth();
	}
	
	@Override
	public void init() {
		//	Content
		setLayout(new BorderLayout());
		posTable = new POSTable();
		orderLineTableHandle = new POSOrderLineTableHandle(posTable);
		CScrollPane scroll = new CScrollPane(posTable);
		scroll.addKeyListener(this);
		orderLineTableHandle.prepareTable();
		posTable.getModel().addTableModelListener(this);
		posTable.addKeyListener(this);
		posTable.addMouseListener(this);
		posTable.setFillsViewportHeight(true); //@Trifon
		posTable.growScrollbars();
		setMaximumSize(new Dimension(50, 50));
		setPreferredSize(new Dimension(50, 50));
		add(scroll, BorderLayout.CENTER);
		addKeyListener(this);
	} //init

	@Override
	public void actionPerformed(ActionEvent e) {
		String action = e.getActionCommand();
		if (action == null || action.length() == 0)
			return;
		log.info( "POSOrderLinePanel - actionPerformed: " + action);
		
		//	Refresh All
		posPanel.refreshPanel();
	} //	actionPerformed
	
	@Override
	public void focusGained (FocusEvent e) {
		log.info("POSOrderLinePanel - focusGained: " + e);		
	}	//	focusGained
		

	@Override
	public void focusLost (FocusEvent e) {
		if (e.isTemporary())
			return;
		log.info( "POSDocumentPanel - focusLost");
		posPanel.refreshPanel();
	}	//	focusLost

	@Override
	public void valueChanged(ListSelectionEvent e) {
		if ( e.getValueIsAdjusting() )
			return;
		int row = posTable.getSelectedRow();
		if (row != -1 ) {
			Object data = posTable.getModel().getValueAt(row, POSOrderLineTableHandle.POSITION_C_ORDER_ID);
			if ( data != null )	{
				Integer id = (Integer) ((IDColumn)data).getRecord_ID();
				orderLineId = id;
			}
		}
		//	Refresh
		posPanel.refreshPanel();
		
	}  // valueChanged	
	
	@Override
    public void tableChanged(TableModelEvent e) {
		
        boolean isUpdate = (e.getType() == TableModelEvent.UPDATE);
        int row = e.getFirstRow();
		int col = e.getColumn();
		//  Not a table update

		if(col == POSOrderLineTableHandle.POSITION_DELETE) {
			//	Remove Listener
    		posTable.getModel().removeTableModelListener(this);

			IDColumn key = (IDColumn) posTable.getValueAt(row, POSOrderLineTableHandle.POSITION_C_ORDER_ID);
			orderLineId = key.getRecord_ID();
			posPanel.deleteLine(orderLineId);
			
			((DefaultTableModel) posTable.getModel()).removeRow(row);
			posTable.getModel().addTableModelListener(this);
			posPanel.refreshHeader();
			posTable.getModel().addTableModelListener(this);
			//	Only Refresh Header
			posPanel.refreshHeader();
			//	Request Focus
			posTable.requestFocusInWindow();
			//	Exit
			return;
		}
		if (!isUpdate
				|| (col != POSOrderLineTableHandle.POSITION_QTYORDERED
						&& col != POSOrderLineTableHandle.POSITION_PRICE)) {
			return;
		}
		//	Get ID
		IDColumn key = (IDColumn) posTable.getValueAt(row, POSOrderLineTableHandle.POSITION_C_ORDER_ID);
		
		//	Validate Key
		if (key != null) {
			//	Set Current Order Line
			orderLineId = key.getRecord_ID();
    		//	Get Values
    		BigDecimal m_QtyOrdered = (BigDecimal) posTable.getValueAt(row, POSOrderLineTableHandle.POSITION_QTYORDERED);
    		BigDecimal m_Price = (BigDecimal) posTable.getValueAt(row, POSOrderLineTableHandle.POSITION_PRICE);
    		
    		posPanel.setQuantity(m_QtyOrdered);
			posPanel.setPrice(m_Price);
			updateLine();
    		
    	}
    }

	public void updateLine() {
		int row = posTable.getSelectedRow();
		BigDecimal qtyOrdered = posPanel.getQty();
		BigDecimal price = posPanel.getPrice();
		//	Remove Listener
		posTable.getModel().removeTableModelListener(this);
		//	Remove line
		if(qtyOrdered != null && qtyOrdered.signum() <= 0) {
			if (orderLineId > 0)
				posPanel.deleteLine(orderLineId);
			if (row > 0) {
				((DefaultTableModel) posTable.getModel()).removeRow(row);
				posTable.getModel().addTableModelListener(this);
				posPanel.refreshHeader();
			}
			//	Exit
			return;
		}
		
		//	Get Order Line
		BigDecimal[] summary = posPanel.updateLine(orderLineId, qtyOrdered, price);
		//	Set Totals
		if(summary != null && row >= 0) {
			posTable.setValueAt(summary[0], row, POSOrderLineTableHandle.POSITION_LINENETAMT);
			posTable.setValueAt(summary[2], row, POSOrderLineTableHandle.POSITION_GRANDTOTAL);
		}
		posTable.getModel().addTableModelListener(this);
		//	Only Refresh Header
		posPanel.refreshHeader();
		posPanel.refreshPanel();
		//	Request Focus
		posTable.requestFocusInWindow();
		return;
	}
	
	
	@Override
	public void refreshPanel() {
		//	Remove Listener
		posTable.getModel().removeTableModelListener(this);
		//	Set Editable Columns
		orderLineTableHandle.setEditable(posPanel.isModifyPrice(), posPanel.isDrafted());
		//	
		if (!posPanel.hasOrder()) {
			posTable.loadTable(new PO[0]);
		}
		//	Load Data
		orderLineTableHandle.loadTable(posPanel.getC_Order_ID());
		//	
		for (int i = 0; i < posTable.getRowCount(); i ++ ) {
			IDColumn key = (IDColumn) posTable.getModel().getValueAt(i, POSOrderLineTableHandle.POSITION_C_ORDER_ID);
			if ( key != null && orderLineId > 0 && key.getRecord_ID() == orderLineId) {
				posTable.getSelectionModel().setSelectionInterval(i, i);
				break;
			}
		}
		//	Auto Size
		posPanel.autoSize();
		//	Add Listener
		posTable.getModel().addTableModelListener(this);
	}


	@Override
	public String validatePanel() {
		return null;
	}


	@Override
	public void changeViewPanel() {
		int row = posTable.getSelectedRow();
		if (row != -1 &&  row < posTable.getRowCount()) {
			//	Set Current Order Line
			BigDecimal qtyOrdered = (BigDecimal) posTable.getValueAt(row, POSOrderLineTableHandle.POSITION_QTYORDERED);
			BigDecimal price = (BigDecimal) posTable.getValueAt(row, POSOrderLineTableHandle.POSITION_PRICE);
			posPanel.setQuantity(qtyOrdered);
			posPanel.setPrice(price);
			posPanel.changeViewQuantityPanel();
		}
		else {
			posPanel.setQuantity(Env.ZERO);
			posPanel.setPrice(Env.ZERO);
		}
	}


	@Override
	public void keyTyped(KeyEvent e) {
		
	}


	@Override
	public void keyPressed(KeyEvent e) {
		
	}


	@Override
	public void keyReleased(KeyEvent e) {
		int row = posTable.getSelectedRow();
		if(row < 0) {
			e.consume();
			return;
		}
		//	
		posTable.setRowChecked(row, true);
		//	
		switch (e.getKeyCode()) {
			case KeyEvent.VK_ALT:
				break;
			case KeyEvent.VK_O:
				posTable.editCellAt(row, POSOrderLineTableHandle.POSITION_QTYORDERED, e);
				break;
			case KeyEvent.VK_P:
				posTable.editCellAt(row, POSOrderLineTableHandle.POSITION_PRICE, e);
				break;
			case KeyEvent.VK_UP:
				showProductInfo(row);
				break;
			case KeyEvent.VK_DOWN:
				showProductInfo(row);
				break;
			default:
				break;
		}		
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		
	}

	@Override
	public void mousePressed(MouseEvent e) {
		POSTable c_table = (POSTable)e.getSource();
		int row = c_table.getSelectedRow();
		int column = c_table.getSelectedColumn();
		if(column == POSOrderLineTableHandle.POSITION_DELETE) {
			posTable.getModel().removeTableModelListener(this);
			IDColumn key = (IDColumn) c_table.getValueAt(row, 0);
			orderLineId = key.getRecord_ID();
			posPanel.deleteLine(orderLineId);

			((DefaultTableModel) posTable.getModel()).removeRow(row);
			posTable.getModel().addTableModelListener(this);
			posPanel.refreshHeader();
			return;
		}
		if (row != -1)	{
			posPanel.changeViewPanel();
			showProductInfo(row);
		}
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		
	}


	@Override
	public void mouseExited(MouseEvent e) {
		
	}
	
	/**
	 * Show Product Info
	 * @param row
	 * @return void
	 */
	private void showProductInfo(int row) {
		Object data = posTable.getModel().getValueAt(row, 0);
		if ( data != null )	{
			Integer id = (Integer) ((IDColumn)data).getRecord_ID();
			orderLineId = id;
			int m_M_Product_ID = DB.getSQLValue(null, "SELECT ol.M_Product_ID "
					+ "FROM C_OrderLine ol "
					+ "WHERE ol.C_OrderLine_ID = ?", orderLineId);
			//	Refresh
			posPanel.refreshProductInfo(m_M_Product_ID);
		}
	}

	@Override
	public void moveUp() {
		 int rows = posTable.getRowCount();
		 if (rows == 0)
		 return;
		 int row = posTable.getSelectedRow();
		 row--;
		 if (row < 0)
		 row = 0;
		 posTable.getSelectionModel().setSelectionInterval(row, row);
		 posPanel.changeViewPanel();
		return;
	}

	@Override
	public void moveDown() {
		 int rows = posTable.getRowCount();
		 if (rows == 0)
			 return;
		 int row = posTable.getSelectedRow();
		 row++;
		 if (rows == row) {
			 row--;
		 }

		 if (row < 0)
			 row = 0;
		posTable.getSelectionModel().setSelectionInterval(row, row);
		posPanel.changeViewPanel();
		return;
	}

	public int getC_OrderLine_ID()
	{
		return orderLineId;
	}
} //	POSOrderLinePanel