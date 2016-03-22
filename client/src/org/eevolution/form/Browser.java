/******************************************************************************
 * Product: ADempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2003-2011 e-Evolution Consultants. All Rights Reserved.      *
 * Copyright (C) 2003-2011 Victor Pérez Juárez 								  * 
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Contributor(s): Victor Pérez Juárez  (victor.perez@e-evolution.com)		  *
 * Sponsors: e-Evolution Consultants (http://www.e-evolution.com/)            *
 *****************************************************************************/
package org.eevolution.form;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.exceptions.DBException;
import org.adempiere.impexp.ArrayExcelExporter;
import org.adempiere.model.I_AD_View_Column;
import org.adempiere.model.MBrowse;
import org.adempiere.model.MBrowseField;
import org.adempiere.model.MView;
import org.adempiere.model.MViewColumn;
import org.adempiere.model.MViewDefinition;
import org.compiere.minigrid.IDColumn;
import org.compiere.model.GridField;
import org.compiere.model.GridFieldVO;
import org.compiere.model.I_AD_Column;
import org.compiere.model.MColumn;
import org.compiere.model.MLookup;
import org.compiere.model.MLookupFactory;
import org.compiere.model.MProcess;
import org.compiere.model.MQuery;
import org.compiere.model.MRole;
import org.compiere.model.MTable;
import org.compiere.model.Query;
import org.compiere.process.ProcessInfo;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Language;
import org.compiere.util.Msg;
import org.eevolution.grid.IBrowseTable;
import org.eevolution.grid.IBrowserRows;

/**
 * Abstract Smart Browser <li>FR [ 3426137 ] Smart Browser
 * https://sourceforge.net
 * /tracker/?func=detail&aid=3426137&group_id=176962&atid=879335
 * @author Yamel Senih, ysenih@erpcya.com, ERPCyA http://www.erpcya.com
 * 		<li>BR [ 242 ] Parent Window Number, is not put in Browse constructor
 * 		@see https://github.com/adempiere/adempiere/issues/242
 * 		<li>FR [ 245 ] Change Smart Browse to MVC
 * 		@see https://github.com/adempiere/adempiere/issues/245
 * 		<li>FR [ 246 ] Smart Browse validate parameters when is auto-query
 * 		@see https://github.com/adempiere/adempiere/issues/246
 * 
 */
public abstract class Browser {
	static public LinkedHashMap<String, Object> getBrowseValues(
			int AD_PInstance_ID, String alias, int recordId, String trxName) {
		LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		List<Object> parameters = new ArrayList<Object>();
		try {
			StringBuilder sql = new StringBuilder(
					"SELECT ColumnName , Value_String, Value_Date , Value_Number FROM T_Selection_Browse "
							+ "WHERE  AD_PInstance_ID=? AND T_Selection_ID=? ");
			parameters.add(AD_PInstance_ID);
			parameters.add(recordId);
			
			if(alias != null)
			{	
				sql.append("AND ColumnName LIKE ?");
				parameters.add(alias.toUpperCase() + "_%");
			}	
			pstmt = DB.prepareStatement(sql.toString(), trxName);
			DB.setParameters(pstmt, parameters);
			rs = pstmt.executeQuery();
			while (rs.next()) {
				String columnName = rs.getString(1);
				/*if (columnName.indexOf("_") > 0)
					columnName = columnName
							.substring(columnName.indexOf("_") + 1);*/
				String valueString = rs.getString(2);
				Timestamp valueDate = rs.getTimestamp(3);
				BigDecimal valueBigDecimal = rs.getBigDecimal(4);
				if (valueString != null) {
					values.put(columnName, valueString);
					continue;
				} else if (valueDate != null) {
					values.put(columnName, valueDate);
					continue;
				} else if (valueBigDecimal != null) {
					values.put(columnName, valueBigDecimal);
					continue;
				} else values.put(columnName, valueString);
			}

		} catch (SQLException ex) {
			throw new DBException(ex);
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}

		return values;
	}
	
	/** Smart Browse */
	public MBrowse m_Browse = null;
	/** Smart View */
	public MView m_View = null;

	public static final int WINDOW_WIDTH = 1024; // width of the window

	/** list of query columns */
	public ArrayList<String> m_queryColumns = new ArrayList<String>();
	/** list of query columns (SQL) */
	public ArrayList<String> m_queryColumnsSql = new ArrayList<String>();
	
	/** Parameters */
	protected ArrayList<Object> m_parameters;
	/** Parameters */
	protected ArrayList<Object> m_parameters_values;
	/** Parameters */
	protected ArrayList<GridFieldVO> m_parameters_field;
	/** Cache m_whereClause **/
	protected String m_whereClause = ""; 
	
	/** MProcess process */
	public MProcess m_process = null;
	/** ProcessInfo */
	public ProcessInfo m_pi = null;
	/** Browse Process Info */
	public ProcessInfo m_browse_pi = null;

	/** Loading success indicator */
	public boolean p_loadedOK = false;
	/** Model Index of Key Column */
	public int m_keyColumnIndex = -1;
	/** OK pressed */
	public boolean m_ok = false;
	/** Cancel pressed - need to differentiate between OK - Cancel - Exit */
	public boolean m_cancel = false;
	/** Result IDs */
	public ArrayList<Integer> m_results = new ArrayList<Integer>(3);
	/** Result Values */
	public LinkedHashMap<Integer,LinkedHashMap<String, Object>> m_values = new LinkedHashMap<Integer,LinkedHashMap<String,Object>>();
	/** Logger */
	public CLogger log = CLogger.getCLogger(getClass());

	/** Layout of Grid */
	public List<MBrowseField> browserFields;

	public String m_sqlMain;
	/** Count SQL Statement */
	public String m_sqlCount;
	/** Order By Clause */
	public String m_sqlOrderBy;
	/** Master (owning) Window */
	public int windowNo;
	/** Table Name */
	public String p_FromClause;
	/** Key Column Name */
	public String p_keyColumn;
	/** Enable more than one selection */
	public boolean p_multiSelection;
	/** Initial WHERE Clause */
	public String p_whereClause = "";
	/** Window Width */
	public static final int INFO_WIDTH = 800;
	public boolean isAllSelected = false;
	/** Exporter */
	private Exporter m_exporter = null;
	/** Language **/
	private Language m_language = null;
	/** Export rows **/
	protected ArrayList<ArrayList<Object>> m_rows = new ArrayList<ArrayList<Object>>();
	//	BR [ 242 ]
	private int parentWindowNo;
	
	
	protected boolean isCollapsibleByDefault = true;
	protected boolean isSelectedByDefault = false;
	protected boolean isExecuteQueryByDefault = false;
	protected boolean isDeleteable = true;
	protected boolean isShowTotal = false;
	protected int     AD_Window_ID = 0;

	/**
	 * Standard Contructor
	 * @param modal
	 * @param WindowNo
	 * @param value
	 * @param browse
	 * @param keyColumn
	 * @param multiSelection
	 * @param where
	 */
	public Browser(boolean modal, int WindowNo, String value, MBrowse browse,
                   String keyColumn, boolean multiSelection, String where) {
		m_Browse = browse;
		m_View = browse.getAD_View();
		p_keyColumn = keyColumn;
		p_multiSelection = multiSelection;
		m_language = Language.getLanguage(Env
				.getAD_Language(m_Browse.getCtx()));

		
		isCollapsibleByDefault = browse.isCollapsibleByDefault();
		isDeleteable = browse.isDeleteable();
		isSelectedByDefault = browse.isSelectedByDefault();
		isExecuteQueryByDefault = browse.isExecutedQueryByDefault();
		isShowTotal = browse.isShowTotal();
		
		AD_Window_ID = browse.getAD_Window_ID();
		//	
		parentWindowNo = WindowNo;
		
		log.info(m_Browse.getName() + " - " + keyColumn + " - " + p_whereClause);
	}
	
	/**
	 * Parse context in where clause
	 * @param where
	 */
	public void setContextWhere(String where) {
		p_whereClause = null;
		
		String whereClause = where != null ? where : "";

		if(m_Browse.getWhereClause() != null )
			   whereClause = whereClause + m_Browse.getWhereClause();
		else
				whereClause = " 1=1 ";
		if (whereClause.indexOf('@') == -1)
			p_whereClause = whereClause;
		else {
			//	BR [ 242 ]
			p_whereClause = Env.parseContext(Env.getCtx(), getWindowNo(),
					whereClause, false, false);
			if (p_whereClause.length() == 0)
				log.log(Level.SEVERE, "Cannot parse context= " + whereClause);
		}

		log.info(m_Browse.getName() + " - " + p_whereClause);
	}
	
	/**
	 * Copy Context from parent window
	 * @param fromWindowNo
	 * @param toWindowNo
	 */
	public void copyWinContext() {
		//
		Object[] keys = Env.getCtx().keySet().toArray();
		for (int i = 0; i < keys.length; i++) {
			String tag = keys[i].toString();
			if (tag.startsWith(getParentWindowNo()+"|")) {
				String context = tag.substring(tag.lastIndexOf("|") + 1);
				String value = Env.getContext(Env.getCtx(), getParentWindowNo(), context);
				Env.setContext(Env.getCtx(), getWindowNo(), context, value);
			}
		}
	}	//	copyWinContext
	
	/**
	 * Initialize data of browser
	 * @return
	 */
	public void initBrowserData() {

		browserFields = new ArrayList<MBrowseField>();
		MBrowseField fieldKey =  m_Browse.getFieldKey();
		if(fieldKey != null)
			browserFields.add(fieldKey);
		else
		{
			MViewColumn column = new MViewColumn(m_Browse.getCtx() , 0 , m_Browse.get_TrxName());
			column.setName("Row");
			column.setColumnSQL("'Row' AS \"Row\"");


			MBrowseField browseField = new MBrowseField(m_Browse , column);
			browseField.setAD_Reference_ID(DisplayType.ID);
			browseField.setIsKey(true);
			browseField.setIsReadOnly(false);
		}


		for (MBrowseField field : m_Browse.getDisplayFields()) {

			if (field.isQueryCriteria()) {
				m_queryColumns.add(field.getName());
			}
			m_queryColumnsSql.add(field.getAD_View_Column().getColumnSQL());

			if(field.isKey())
				continue;

			// Defines Field as Y-Axis
			if(field.getAxis_Column_ID() > 0)
			{
				for (MBrowseField fieldAxis : getInfoColumnForAxisField(field)){
					browserFields.add(fieldAxis);
				}
				continue;
			}
			browserFields.add(field);
		}
	}

	public ArrayList<Object> getParameters() {
		return m_parameters;
	}
	
	public ArrayList<Object> getParametersValues() {
		return m_parameters_values;
	}
	
	public void addSQLWhere(StringBuffer sql, int index, String value) {
		if (!(value.equals("") || value.equals("%"))
				&& index < m_queryColumns.size()) {
			// sql.append(" AND UPPER(").append(m_queryColumnsSql.get(index).toString()).append(") LIKE '");
			sql.append(" UPPER(")
					.append(m_queryColumnsSql.get(index).toString())
					.append(") LIKE '");
			sql.append(value);
			if (value.endsWith("%"))
				sql.append("'");
			else
				sql.append("%'");
		}
	}

	/**
	 * Test Row Count
	 * 
	 * @return > 0 if display
	 */
	public int testCount() {
		int no = -1;

		no = getCount();
		// log.fine("#" + no + " - " + (System.currentTimeMillis()-start) +
		// "ms");
		MRole role = MRole.getDefault();
		if (role.isQueryMax(no))
			return no;
		//	Default
		return -1;
	} // testCount
	
	/**
	 * Get row Quantity
	 * @return
	 */
	public int getCount() {
		long start = System.currentTimeMillis();
		String dynWhere = getSQLWhere(true);
		StringBuffer sql = new StringBuffer(m_sqlCount);
		if (dynWhere.length() > 0)
			sql.append(dynWhere); // includes first AND
		String countSql = Msg.parseTranslation(Env.getCtx(), sql.toString()); // Variables
		countSql = MRole.getDefault().addAccessSQL(countSql,
				m_View.getParentEntityAliasName(), MRole.SQL_FULLYQUALIFIED,
				MRole.SQL_RO);
		log.finer(countSql);
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		int no = -1;
		try {
			pstmt = DB.prepareStatement(countSql, null);
			if (getParametersValues().size() > 0)
				DB.setParameters(pstmt, getParametersValues());
			rs = pstmt.executeQuery();
			if (rs.next())
				no = rs.getInt(1);
		} catch (Exception e) {
			log.log(Level.SEVERE, countSql, e);
			no = -2;
		} finally {
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		log.fine("#" + no + " - " + (System.currentTimeMillis() - start) + "ms");

		return no;
	}
	
	/**
	 * BR [ 246 ]
	 * Validate if has mandatory parameters
	 * @return
	 */
	public boolean hasMandatoryParams() {
		for (Entry<Object, GridField> entry : getPanelParameters().entrySet()) {
			GridField editor = (GridField) entry.getValue();
			if(editor.isMandatory(true))
				return true;
		}
		//	Default
		return false;
	}
	
	/**
	 * FR [ 245 ]
	 * Initialize process info
	 */
	public void initProcessInfo() {
		m_process = MProcess.get(Env.getCtx(), m_Browse.getAD_Process_ID());
		m_browse_pi = new ProcessInfo(m_process.getName(), m_Browse.getAD_Process_ID());
		m_browse_pi.setAD_User_ID(Env.getAD_User_ID(Env.getCtx()));
		m_browse_pi.setAD_Client_ID(Env.getAD_Client_ID(Env.getCtx()));
		m_browse_pi.setWindowNo(getWindowNo());
	}
	
	/**
	 * FR [ 245 ]
	 * Get Where Clause
	 * @param refresh
	 * @return
	 */
	public String getSQLWhere(boolean refresh) {
		
		if(!refresh)
			return m_whereClause;
		
		//	Valid null
		LinkedHashMap<Object, GridField> panelParameters = getPanelParameters();
		if(panelParameters == null
				|| panelParameters.size() == 0)
			return m_whereClause;
		//	
		m_parameters_values = new ArrayList<Object>();
		m_parameters = new ArrayList<Object>();

		boolean onRange = false;
		StringBuilder sql = new StringBuilder(p_whereClause);

		for (Entry<Object, GridField> entry : panelParameters.entrySet()) {
			GridField editor = (GridField) entry.getValue();
			GridFieldVO field = editor.getVO();
			if (!onRange) {

				if (editor.getValue() != null
						&& !editor.getValue().toString().isEmpty()
						&& !field.isRange) {
					sql.append(" AND ");
					if(DisplayType.String == field.displayType)
					{
						if (field.ColumnName.equals("Value")
								|| field.ColumnName.equals("DocumentNo"))
						{
							String value = (String)editor.getValue();
							if (value.contains(","))
							{
								value = value.replace(" ", "");
								String token;
								String inStr = new String(value);
								StringBuffer outStr = new StringBuffer("(");
								int i = inStr.indexOf(',');
								while (i != -1)
								{
									outStr.append("'" + inStr.substring(0, i) + "',");	
									inStr = inStr.substring(i+1, inStr.length());
									i = inStr.indexOf(',');

								}
								outStr.append("'" + inStr + "')");
								sql.append(field.Help).append(" IN ")
								.append(outStr);
							}						
						}
						else
						{
							sql.append(field.Help).append(" LIKE ? ");
							m_parameters.add(field.Help);
							m_parameters_values.add("%" + editor.getValue() + "%");								
						}		
					}
					else
					{
						sql.append(field.Help).append("=? ");
						m_parameters.add(field.Help);
						m_parameters_values.add(editor.getValue());
					}
				} 
				else if (editor.getValue() != null
						&& !editor.getValue().toString().isEmpty()
						&& field.isRange) {
					sql.append(" AND ");
					//sql.append(field.Help).append(" BETWEEN ?");
					sql.append(field.Help).append(" >= ? ");
					m_parameters.add(field.Help);
					m_parameters_values.add(editor.getValue());
					onRange = true;
				}
				else if (editor.getValue() == null
						&& field.isRange) {
					onRange = true;
				} else
					continue;
			} else if (editor.getValue() != null
					&& !editor.getValue().toString().isEmpty()) {
				//sql.append(" AND ? ");
				sql.append(" AND ").append(field.Help).append(" <= ? ");
				m_parameters.add(field.Help);
				m_parameters_values.add(editor.getValue());
				onRange = false;
			}
			else
				onRange = false;
		}
		m_whereClause = sql.toString();
		return sql.toString();
	}
	
	/**
	 * FR [ 245 ]
	 * Set Parameters
	 */
	public void setParameters() {
		m_parameters_values = new ArrayList<Object>();
		m_parameters = new ArrayList<Object>();
		m_parameters_field = new ArrayList<GridFieldVO>();
		boolean onRange = false;
		
		for (Entry<Object, GridField> entry : getPanelParameters().entrySet()) {
			GridField editor = (GridField) entry.getValue();
			GridFieldVO field = editor.getVO();
			if (!onRange) {

				if (editor.getValue() != null
						&& !editor.getValue().toString().isEmpty()
						&& !field.isRange) {
					m_parameters.add(field.Help);
					m_parameters_values.add(editor.getValue());
					m_parameters_field.add(field);
				} else if (editor.getValue() != null
						&& !editor.getValue().toString().isEmpty()
						&& field.isRange) {
					m_parameters.add(field.Help);
					m_parameters_values.add(editor.getValue());
					m_parameters_field.add(field);
					onRange = true;
				} else
					continue;
			} else if (editor.getValue() != null
					&& !editor.getValue().toString().isEmpty()) {
				m_parameters.add(field.Help);
				m_parameters_values.add(editor.getValue());
				m_parameters_field.add(field);
				onRange = false;
			}
		}
	}

	/**
	 * FR [ 245 ]
	 * Get a Parameter value from a key
	 * @param key
	 * @return
	 */
	public Object getParameterValue(Object key) {
		GridField field = getPanelParameters().get(key);
		//	
		if(field != null)
			return field.getValue();
		else
			return null;
	}
	
	/**
	 * FR [ 245 ]
	 * Evaluate Mandatory Filter
	 * @return String
	 */
	public String evaluateMandatoryFilter() {
		Object value_from=null;
		boolean onRange = false;
		StringBuffer mandatorytoFill = new StringBuffer();
		for (Entry<Object, GridField> entry : getPanelParameters().entrySet()) {
			GridField editor = (GridField) entry.getValue();
			GridFieldVO field = editor.getVO();
			if (!onRange) {

				if ((editor.getValue() == null
						|| (editor.getValue() != null && editor.getValue().toString().isEmpty()))
						&& !field.isRange
						&& editor.isMandatory(true)) {
					if(mandatorytoFill.length() > 0) {
						mandatorytoFill.append(", ");
					}
					//	You must Fill
					mandatorytoFill.append("@").append(field.ColumnName).append("@");
				} else if (editor.getValue() != null
						&& !editor.getValue().toString().isEmpty()
						&& field.isRange
						&& editor.isMandatory(true)) {
					onRange = true;
					value_from =editor.getValue();
				}else if (editor.getValue() == null
						&& field.isRange
						&& editor.isMandatory(true)) {
					onRange = true;
					value_from = null;
				}
				else
					continue;
			} else if ((editor.getValue() == null
					|| (editor.getValue() != null && editor.getValue().toString().isEmpty()))
					&& editor.isMandatory(true)) {
				if (value_from!=null){
					value_from=null;
					onRange = false;
				}
				else
				{
					if(mandatorytoFill.length() > 0) {
						mandatorytoFill.append(", ");
					}
					//	You must Fill
					mandatorytoFill.append("@").append(field.ColumnName).append("@");
				}
			}
			else{
				onRange = false;
				value_from=null;
			}

		}
		//	Valid null
		if(mandatorytoFill.length() > 0) {
			return mandatorytoFill.toString();
		}
		//	Default
		return null;
	}
	
	/**
	 * FR [ 245 ]
	 * save result values
	 * @param browserTable
	 */
	protected void saveResultSelection(IBrowseTable browserTable) {
		if (m_keyColumnIndex == -1) {
			return;
		}
		//	Verify if is Multi-Selection
		if (p_multiSelection) {
			int rows = browserTable.getRowCount();
			IBrowserRows browserRows = browserTable.getData();
			m_values = new LinkedHashMap<Integer,LinkedHashMap<String,Object>>();
			for (int row = 0; row < rows; row++) {
				//Find the IDColumn Key
				Object data = browserRows.getValue(row,
						m_keyColumnIndex);
				if (data instanceof IDColumn) {
					IDColumn dataColumn = (IDColumn) data;
					if (dataColumn.isSelected()) {
						LinkedHashMap<String, Object> values = new LinkedHashMap<String, Object>();
						for(int col = 0 ; col < browserRows.getColumnCount(); col++)
						{
							MBrowseField field = browserRows.getBrowserField(col);
							if (!field.isReadOnly() || field.isIdentifier() )
							{
								GridField gridField = (GridField) browserRows.getValue(row, col);
								Object value = gridField.getValue();
								values.put(field.getAD_View_Column().getColumnName(), value);
							}
						}
						if(values.size() > 0)
							m_values.put(dataColumn.getRecord_ID(), values);
					}
				}
			}
		}
	}
	
	/**
	 * FR [ 245 ]
	 * Get Data of rows
	 * @param browserTable
	 * @return
	 */
	public  ArrayList<ArrayList<Object>> getDataRows(IBrowseTable browserTable) {
		ArrayList<ArrayList<Object>> rows = m_rows;
		if (isShowTotal()) {
			ArrayList<Object> row = new ArrayList<Object>();
			int lastRow = browserTable.getRowCount() - 1;
			for (int column = 0; column <= browserTable.getColumnCount() - 1 ; column++) {
				Object data = browserTable.getValueAt(lastRow , column);
				if (data == null)
					row.add(null);
				else
					row.add(data);
			}
			rows.add(row);
		}
		return rows;
	}
	
	/**
	 * FR [ 245 ]
	 * Save Selection - Called by dispose
	 * @param browserTable
	 */
	protected void saveSelection(IBrowseTable browserTable) {
		// Already disposed
		if (browserTable == null)
			return;

		log.config("OK=" + m_ok);
		if (!m_ok) // did not press OK
		{
			m_results.clear();
//			browserTable.removeAll();
//			browserTable = null;
			return;
		}

		// Multi Selection
		if (p_multiSelection) {
			m_results.clear();
			m_results.addAll(getSelectedRowKeys(browserTable));
		} else // singleSelection
		{
			Integer data = getSelectedRowKey(browserTable);
			if (data != null)
				m_results.add(data);
		}

		// Save Settings of detail info screens
		// saveSelectionDetail();
		// Clean-up
//		browserTable.removeAll();
//		browserTable = null;
	} // saveSelection
	
	/**
	 * FR [ 245 ]
	 */
	public void selectedRows(IBrowseTable browserTable) {
		int topIndex = browserTable.isShowTotals() ? 2 : 1;
		int rows = browserTable.getRowCount();

		if (isAllSelected) {
			for (int row = 0; row <= rows - topIndex; row++) {
				Object data = browserTable.getValueAt(row, m_keyColumnIndex);
				if (data instanceof IDColumn) {
					IDColumn dataColumn = (IDColumn) data;
					dataColumn.setSelected(true);
					browserTable.setValueAt(dataColumn, row, m_keyColumnIndex);
				}
			}

		} else {
			for (int row = 0; row <= rows - topIndex; row++) {
				Object data = browserTable.getValueAt(row, m_keyColumnIndex);
				if (data instanceof IDColumn) {
					IDColumn dataColumn = (IDColumn) data;
					dataColumn.setSelected(false);
					browserTable.setValueAt(dataColumn, row, m_keyColumnIndex);
				}
			}
		}
		isAllSelected = !isAllSelected;
	}
	
	/**
	 * Get the keys of selected row/s based on layout defined in prepareTable
	 * @param browseTable
	 * @return IDs if selection present
	 */
	public ArrayList<Integer> getSelectedRowKeys(IBrowseTable browseTable) {
		ArrayList<Integer> selectedDataList = new ArrayList<Integer>();

		if (m_keyColumnIndex == -1) {
			return selectedDataList;
		}

		if (p_multiSelection) {
			int rows = browseTable.getRowCount();
			for (int row = 0; row < rows; row++) {
				Object data = browseTable.getValueAt(row,
						m_keyColumnIndex);
				if (data instanceof IDColumn) {
					IDColumn dataColumn = (IDColumn) data;
					if (dataColumn.isSelected()) {
						selectedDataList.add(dataColumn.getRecord_ID());
					}
				}
			}
		}

		if (selectedDataList.size() == 0) {
			int row = browseTable.getSelectedRow();
			if (row != -1 && m_keyColumnIndex != -1) {
				Object data = browseTable.getValueAt(row,
						m_keyColumnIndex);
				if (data instanceof IDColumn)
					selectedDataList.add(((IDColumn) data).getRecord_ID());
				if (data instanceof Integer)
					selectedDataList.add((Integer) data);
			}
		}

		return selectedDataList;
	}
	
	/**
	 * Init info with Table. - find QueryColumns (Value, Name, ..) - build
	 * gridController & column
	 * @param table table to initialize
	 * @return void
	 */
	public void initBrowserTable(IBrowseTable table) {
		if(browserFields != null)
			return;
		//	
		initBrowserData();
		
		log.finest("Browse Fields #" + browserFields.size());
		//	
		prepareTable(table);
	} // initInfoTable
	
	/**************************************************************************
	 * Prepare Table, Construct SQL (m_m_sqlMain, m_sqlAdd) and size Window
	 * @param table table to prepare
	 * @param fields list
	 * @param from from clause
	 * @param staticWhere where clause
	 * @param orderBy order by clause
	 */
	private void prepareTable(IBrowseTable table) {
		//	Get values
		setContextWhere(null);
		String from = m_View.getFromClause();
		//	
		StringBuffer sql = new StringBuffer("SELECT DISTINCT ");
		sql.append(table.prepareTable(browserFields, p_multiSelection));
		// Table Selection (Invoked before setting column class so that row
		// selection is enabled)
		table.setMultiSelection(p_multiSelection);
		table.setShowTotals(m_Browse.isShowTotal());
		//	
		sql.append(" FROM ").append(from);
		sql.append(" WHERE ");
		m_sqlMain = sql.toString();
		m_sqlCount = "SELECT COUNT(*) FROM " + from + " WHERE ";
		m_sqlOrderBy = getSQLOrderBy();

		if (m_keyColumnIndex == -1)
			log.log(Level.WARNING, "No KeyColumn - " + sql);
	} // prepareTable
	
	public void setProcessInfo(ProcessInfo pi) {
		m_pi = pi;
		if(m_pi != null)
			if(	m_browse_pi !=null)
				m_browse_pi.setRecord_ID(m_pi.getRecord_ID());
	}

	public ProcessInfo getProcessInfo() {
		return m_pi;
	}

	public void setBrowseProcessInfo(ProcessInfo pi) {
		m_browse_pi = pi;
	}

	public ProcessInfo getBrowseProcessInfo() {
		return m_browse_pi;
	}
	
	public String getKeyColumn() {
		if(p_keyColumn == null || p_keyColumn.isEmpty())
			p_keyColumn = m_Browse.getFieldKey().getAD_View_Column().getAD_Column().getColumnName();
		
		return p_keyColumn;
	}

	/**
	 * Get a Selected key from row
	 * @param browseTable
	 * @return
	 */
	public Integer getSelectedRowKey(IBrowseTable browseTable) {
		ArrayList<Integer> selectedDataList = getSelectedRowKeys(browseTable);
		if (selectedDataList.size() == 0) {
			return null;
		} else {
			return selectedDataList.get(0);
		}
	}

	public List<Integer> getSelectedKeys() {
		if (!m_ok || m_results.size() == 0)
			return null;
		return m_results;
	}

	public Object getSelectedKey() {
		if (!m_ok || m_results.size() == 0)
			return null;
		return m_results.get(0);
	}

	/**
	 * Delete a Selection
	 * @param browseTable
	 * @return
	 */
    protected int deleteSelection(IBrowseTable browseTable) {
        MTable table = null;
        MBrowseField fieldKey  = m_Browse.getFieldKey();
        if (fieldKey != null)
            if (fieldKey.getAD_View_Column().getAD_Column_ID() > 0)
                table = (MTable) fieldKey.getAD_View_Column().getAD_Column().getAD_Table();

        int records = 0 ;
        for (int id : getSelectedRowKeys(browseTable))
        {
            if (table != null)
            {
                table.getPO(id, null).deleteEx(true);
                records++;
            }
        }
        return records;
    }
	
	protected boolean isSelectedByDefault()
	{
		return isSelectedByDefault;
	}
	
	protected boolean isExecuteQueryByDefault()
	{
		return isExecuteQueryByDefault;
	}

	protected boolean isCollapsibleByDefault()
	{
		return isCollapsibleByDefault;
	}
	
	protected boolean isDeleteable()
	{
		return isDeleteable;
	}
	
	protected boolean isShowTotal()
	{
		return isShowTotal;
	}
	protected int getAD_Window_ID()
	{
		return AD_Window_ID;
	}
	
	public int getAD_Browse_ID() {
		return m_Browse.getAD_Browse_ID();
	}

	/**
	 * Get Info_Column for Axis Field
	 * @param field defined as Axis
	 * @return Info_Column with Axis Field
	 */
	public List<MBrowseField> getInfoColumnForAxisField(MBrowseField field)
	{
		List<MBrowseField> list = new ArrayList<MBrowseField>();

		try {
			I_AD_View_Column xcol, pcol, ycol;
			xcol = field.getAD_View_Column();
			pcol = field.getAxis_Parent_Column();
			ycol = field.getAxis_Column();

			String columnName = xcol.getAD_Column().getColumnName();

			MBrowseField fieldKey = ((MBrowse) field.getAD_Browse()).getFieldKey();
			if(fieldKey == null)
				throw new AdempiereException("@NotFound@ @IsKey@");

			MTable xTable = (MTable) ycol.getAD_View_Definition().getAD_Table();
			String xTableName = xTable.getTableName();

			String keyColumn = MQuery.getZoomColumnName(columnName);
			String tableName = MQuery.getZoomTableName(columnName);

			String whereClause =  "";

			if (pcol != null && pcol.getAD_View_Column_ID() > 0)
			{
				MTable parentTable = MTable.get(field.getCtx(), tableName);
				MColumn parentColumn = getParentColumn(parentTable.getAD_Table_ID());
				if (parentColumn == null)
					throw new AdempiereException("@NotFound@ @IsParent@");
				//	BR [ 242 ]
				if(field.getAD_Val_Rule_ID() > 0)
					whereClause = Env.parseContext(Env.getCtx(), getWindowNo() , field.getAD_Val_Rule().getCode(), false);

			}


			MLookup lookup = MLookupFactory.get(Env.getCtx(), 0,
					xcol.getAD_Column_ID(), field.getAD_Reference_ID(),
					m_language, keyColumn, field.getAD_Reference_Value_ID(), false, whereClause);

			int cols = 0;

			StringBuilder axisSql = new StringBuilder("(SELECT ");
			axisSql.append("SUM(")
					.append(ycol.getAD_Column()
							.getColumnName())
					.append(") FROM  ")
					.append(ycol.getAD_View_Definition().getAD_Table().getTableName())
					.append(" WHERE ")
					.append(xTableName)
					.append(".")
					.append(fieldKey.getAD_View_Column().getAD_Column()
							.getColumnName()).append("=")
					.append(fieldKey.getAD_View_Column().getColumnSQL())
					.append(getAxisSQLWhere(ycol))
					.append(" AND ")
					.append(xTableName).append(".")
					.append(xcol.getAD_Column().getColumnName());

			for (int id :  getAxisRecordIds(tableName, whereClause)) {
				cols ++;
				String display =  lookup.getDisplay(id).trim();
				display = display.length() > 12 ? display.substring(1,12) + "_" + cols : display;
				String joinColumn = Msg.translate(m_language, ycol.getAD_Column()
						.getColumnName());
				joinColumn = joinColumn.length() > 15 ? joinColumn.substring(1, 15) :  joinColumn;
				String sqlColName = display + "/" + joinColumn;
				String colName = lookup.getDisplay(id).trim() + "/" + Msg.translate(m_language, ycol.getAD_Column()
						.getColumnName());

				StringBuffer select = new StringBuffer(axisSql);
				select.append("=").append(id).append(")");

				MViewColumn viewColumn = new MViewColumn(field.getCtx() , 0 , field.get_TrxName());
				MViewColumn.copyValues((MViewColumn) ycol, viewColumn);
				viewColumn.setAD_View_Column_ID(ycol.getAD_View_Column_ID());
				viewColumn.setAD_Column_ID(ycol.getAD_Column_ID());
				viewColumn.setColumnSQL(select.toString());
				viewColumn.setColumnName("\"" + sqlColName + "\"");

				MBrowseField browseField = new MBrowseField((MBrowse)field.getAD_Browse() , viewColumn);
				browseField.setAD_Browse_ID(field.getAD_Browse_ID());
				browseField.setAD_Element_ID(field.getAD_Element_ID());
				browseField.setName(colName);
				browseField.setDescription(viewColumn.getDescription());
				browseField.setHelp(viewColumn.getHelp());
				if (viewColumn.get_ID() > 0)
					browseField.setAD_View_Column_ID(viewColumn.getAD_View_Column_ID());
				browseField.setIsActive(true);
				browseField.setIsIdentifier(viewColumn.isIdentifier());
				browseField.setIsRange(false);
				browseField.setIsQueryCriteria(false);
				browseField.setAD_Reference_ID(ycol.getAD_Column().getAD_Reference_ID());
				browseField.setAD_Reference_Value_ID(ycol.getAD_Column().getAD_Reference_Value_ID());
				browseField.setIsKey(false);
				browseField.setIsDisplayed(true);
				browseField.setIsReadOnly(field.isReadOnly());
				browseField.setAD_Element_ID(field.getAD_Element_ID());

				list.add(browseField);
				log.finest("Added Column=" + sqlColName +  " SQL = " + select);
			}

		} catch (Exception e) {
			throw new AdempiereException(e);
		}
		return list;
	}


    private int[] getAxisRecordIds(String tableName, String tableWhereClause) {

        StringBuilder whereClause = new StringBuilder();
        StringBuilder orderBy = new StringBuilder();
        whereClause.append("EXISTS (SELECT 1 FROM AD_Table t WHERE t.TableName=? AND t.AD_Table_ID=AD_Column.AD_Table_ID) AND ");
        whereClause.append(I_AD_Column.COLUMNNAME_IsIdentifier).append("=?");

        List<MColumn> columns =  new Query(Env.getCtx() , I_AD_Column.Table_Name , whereClause.toString(), null)
                .setOnlyActiveRecords(true)
                .setParameters(tableName, true)
                .setOrderBy(I_AD_Column.COLUMNNAME_SeqNo)
                .list();

        int count = 1;
        for (MColumn column : columns)
        {
            orderBy.append(column.getColumnName());
            if (count != columns.size())
                orderBy.append(",");
            count++;
        }

        return new Query(Env.getCtx(), tableName , tableWhereClause, null)
                .setOnlyActiveRecords(true)
                .setOrderBy(orderBy.toString()).getIDs();
    }

    /**
	 * Get Parent Column for Table
	 * @param AD_Table_ID Table ID
	 * @return MColumn
	 */
	private MColumn getParentColumn(int AD_Table_ID)
	{
		String whereClause = MColumn.COLUMNNAME_AD_Table_ID + "=? AND "
				+ MColumn.COLUMNNAME_IsParent + "=? ";
		return new Query(Env.getCtx(), MColumn.Table_Name, whereClause, null)
				.setParameters(AD_Table_ID, true).first();
	}
	
	public MBrowseField getFieldKey()
	{
		MBrowseField fieldKey = m_Browse.getFieldKey();
		return fieldKey;
	}
	
	public boolean IsIdentifierSelection(String columnName)
	{	
		for (MBrowseField field : m_Browse.getIdentifierFields()) {
			if (field.getAD_View_Column().getColumnName().equals(columnName))
				return true;
		}
		return false;
	}
	
	/**
	 * Get Query from Record Identifier
	 * @return
	 */
	public MQuery getMQuery(IBrowseTable browseTable)
	{
		Integer record_ID = getSelectedRowKey(browseTable);

		if (record_ID == null)
			return null;
		
		MBrowseField fieldKey = getFieldKey();
		if(fieldKey == null)
			return null;
		
		MColumn column = fieldKey.getAD_View_Column().getAD_Column();
		String keyColumn = MQuery.getZoomColumnName(column.getColumnName());
		String tableName = column.getAD_Table().getTableName();
		MQuery query = new MQuery(tableName);
		query.addRestriction(keyColumn, MQuery.EQUAL, record_ID);
		return query;
	}
	
	
	/**
	 * get Parameter Value
	 * @param key
	 * @return Object Value
	 */
//	 public abstract Object getParameterValue(Object key);
	 
	 //	FR [ 245 ]
//	 public abstract void setParameters();
	 
//	 abstract public String  getSQLWhere(boolean refresh);
	 
	/**
	 * Get parameter
	 * @return
	 */
	public abstract LinkedHashMap<Object, GridField> getPanelParameters();
	
	/**
	 * Initialize Smart Browse
	 */
	public abstract void init();
	 
	public String getAxisSQLWhere(I_AD_View_Column viewColumn)
	{
		 MViewDefinition viewDefinition = (MViewDefinition) viewColumn.getAD_View_Definition();
		 MTable tableBaseName = (MTable) viewDefinition.getAD_Table();
		 StringBuilder whereAxis = new StringBuilder();
		 boolean onRange = false;
		 setParameters();
		 
			for (int i = 0; i < m_parameters_field.size(); i++) {
                String fieldName = "";
                MColumn  column = tableBaseName.getColumn(m_parameters_field.get(i).ColumnName);
                if (column != null)
                    fieldName = tableBaseName.getTableName() + "." + column.getColumnName();
                else
                    continue;

				if (!onRange) {

					if (m_parameters_values.get(i) != null
							&& !m_parameters_values.get(i).toString().isEmpty()
							&& !m_parameters_field.get(i).isRange) {
						whereAxis.append(" AND ");
						whereAxis.append(fieldName).append("=").append(m_parameters_values.get(i).toString());
					} else if (m_parameters_values.get(i) != null
							&& !m_parameters_values.get(i).toString().isEmpty()
							&& m_parameters_field.get(i).isRange) {
						whereAxis.append(" AND ");
						whereAxis.append(fieldName).append(" BETWEEN ").append(m_parameters_values.get(i).toString());
						onRange = true;
					} else
						continue;
				} else if (m_parameters_values.get(i) != null
						&& !m_parameters_values.get(i).toString().isEmpty()) {
					whereAxis.append(" AND ").append(m_parameters_values.get(i).toString());
					onRange = false;
				}
			}
			
			return whereAxis.toString();
	 }
	
	protected String getSQL() {
		String dynWhere = getSQLWhere(false);
		StringBuilder sql = new StringBuilder(m_sqlMain);
		if (dynWhere.length() > 0)
			sql.append(dynWhere); // includes first AND

		String dataSql = Msg.parseTranslation(Env.getCtx(), sql.toString()); // Variables
		dataSql = MRole.getDefault().addAccessSQL(dataSql,
				m_View.getParentEntityAliasName(), MRole.SQL_FULLYQUALIFIED,
				MRole.SQL_RO);
        dataSql = dataSql + m_sqlOrderBy;
		log.finer(dataSql);
		return dataSql;
	}
	
	/*public String getSQLOrderBy() {
		StringBuilder sqlOrderBy = new StringBuilder();
		for (MBrowseField field : m_Browse.getOrderByFields()) {
			if (sqlOrderBy.length() > 0 && field.isOrderBy())
				sqlOrderBy.append(",");

			if (field.isOrderBy()) {
				int orderByPosition = getOrderByPosition(field
						.getAD_View_Column().getColumnName());
				if (orderByPosition > 0)
					sqlOrderBy.append(orderByPosition);
			}
		}
		return sqlOrderBy.length() > 0 ? " ORDER BY " + sqlOrderBy.toString()
				: "";
	}*/


	/*private int getOrderByPosition(String name)
	{
		int colOffset = 1; // columns start with 1
		for (int col = 0; col < p_layout.length; col++) {
			Info_Column column = p_layout[col];
			String columnName = column.getColSQL().substring(
					column.getColSQL().indexOf("AS ") + 3);

			Class<?> c = column.getColClass();
			int sortBySqlNo = col + colOffset;
			if (c == KeyNamePair.class)
				colOffset++;
			if(name.equals(columnName))
				return sortBySqlNo;
		}
		return 0;
	}*/

	public String getSQLOrderBy() {
		StringBuilder sqlOrderBy = new StringBuilder();
		for (MBrowseField field : m_Browse.getOrderByFields()) {
			if (field.isOrderBy()) {
				int orderByPosition = getOrderByPosition(field);
				if (orderByPosition <= 0)
					continue;

				if (sqlOrderBy.length() > 0)
					sqlOrderBy.append(",");

					sqlOrderBy.append(orderByPosition);
			}
		}
		return sqlOrderBy.length() > 0 ? " ORDER BY " + sqlOrderBy.toString()
				: "";
	}


	private int getOrderByPosition(MBrowseField BrowserField)
	{
		int colOffset = 1; // columns start with 1
		int col = 0;
		for (MBrowseField field : browserFields) {
			int sortBySqlNo = col + colOffset;
			//Class<?> c = DisplayType.getClass(field.getAD_Reference_ID(), true);
			//if (c == KeyNamePair.class)
			//	colOffset++;
			if (BrowserField.getAD_Browse_Field_ID() == field.getAD_Browse_Field_ID())
				return sortBySqlNo;
			col ++;
		}


		return -1;
	}
	
	protected PreparedStatement getStatement(String sql) {
		PreparedStatement stmt = null;
		try {
			stmt = DB.prepareStatement(sql, null);
			if (getParametersValues().size() > 0)
				DB.setParameters(stmt, getParametersValues());
			return stmt;
		} catch (SQLException e) {
			log.log(Level.SEVERE, sql, e);
		}
		return stmt;
	}
	
	/**
	 * Export from Table
	 * @param browserTable
	 * @return
	 */
	protected File exportXLS(IBrowseTable browserTable) {
		File file = null;
		try {
			if (m_exporter != null && m_exporter.isAlive())
				return file;

			m_exporter = new Exporter();
			m_exporter.start();
			while (m_exporter.isAlive())
				;
			
			ArrayList<ArrayList<Object>> rows = getDataRows(browserTable);
			
			if (rows.size() > 1) {

				String path = System.getProperty("java.io.tmpdir");
				String prefix = makePrefix(m_Browse.getName());
				if (log.isLoggable(Level.FINE)) {
					log.log(Level.FINE, "Path=" + path + " Prefix=" + prefix);
				}
				file = File.createTempFile(prefix, ".xls", new File(path));
				ArrayExcelExporter exporter = new ArrayExcelExporter(
						Env.getCtx(), rows);
				exporter.export(file, m_language, false);
			}
		} catch (IOException e) {
			log.log(Level.SEVERE, "", e);
		} catch (Exception e) {
			log.log(Level.SEVERE, "", e);
		}
		return file;
	}

	private String makePrefix(String name) {
		StringBuffer prefix = new StringBuffer();
		char[] nameArray = name.toCharArray();
		for (char ch : nameArray) {
			if (Character.isLetterOrDigit(ch)) {
				prefix.append(ch);
			} else {
				prefix.append("_");
			}
		}
		return prefix.toString();
	}
	
	/**
	 * Insert result values
	 * @param AD_PInstance_ID
	 */
	public void createT_Selection_Browse(int AD_PInstance_ID)
	{
		StringBuilder insert = new StringBuilder();
		insert.append("INSERT INTO T_SELECTION_BROWSE (AD_PINSTANCE_ID, T_SELECTION_ID, COLUMNNAME , VALUE_STRING, VALUE_NUMBER , VALUE_DATE ) VALUES(?,?,?,?,?,?) ");
		for (Entry<Integer,LinkedHashMap<String, Object>> records : m_values.entrySet()) {
			//set Record ID
			
				LinkedHashMap<String, Object> fields = records.getValue();
				for(Entry<String, Object> field : fields.entrySet())
				{
					List<Object> parameters = new ArrayList<Object>();
					parameters.add(AD_PInstance_ID);
					parameters.add(records.getKey());
					parameters.add(field.getKey());
					
					Object data = field.getValue();
					// set Values					
					if (data instanceof IDColumn)
					{
						IDColumn id = (IDColumn) data;
						parameters.add(null);
						parameters.add(id.getRecord_ID());
						parameters.add(null);
					}
					else if (data instanceof String)
					{
						parameters.add(data);
						parameters.add(null);
						parameters.add(null);
					}
					else if (data instanceof BigDecimal || data instanceof Integer || data instanceof Double)
					{
						parameters.add(null);
						if(data instanceof Double)
						{	
							BigDecimal value = BigDecimal.valueOf((Double)data);
							parameters.add(value);
						}	
						else	
							parameters.add(data);
						parameters.add(null);
					}
					else if (data instanceof Integer)
					{
						parameters.add(null);
						parameters.add((Integer)data);
						parameters.add(null);
					}
					else if (data instanceof Timestamp || data instanceof Date)
					{
						parameters.add(null);
						parameters.add(null);
						if(data instanceof Date)
						{
							Timestamp value = new Timestamp(((Date)data).getTime());
							parameters.add(value);
						}
						else 
						parameters.add(data);
					}
					else
					{
						parameters.add(data);
						parameters.add(null);
						parameters.add(null);
					}
					DB.executeUpdateEx(insert.toString(),parameters.toArray() , null);		
						
				}
		}
	}

	/**
	 * Exporter
	 */
	class Exporter extends Thread {
		private PreparedStatement m_pstmt = null;
		private ResultSet m_rs = null;
		private String dataSql = null;

		/**
		 * Do Work (load data)
		 */
		public void run() {
			long start = System.currentTimeMillis();
			int no = 0;
			dataSql = getSQL();
			m_pstmt = getStatement(dataSql);
			m_rows = new ArrayList<ArrayList<Object>>();
			try {
				log.fine("Start query - "
						+ (System.currentTimeMillis() - start) + "ms");
				m_rs = m_pstmt.executeQuery();
				log.fine("End query - " + (System.currentTimeMillis() - start)
						+ "ms");
				boolean isFirstRow = true;
				while (m_rs.next()) {
					if (this.isInterrupted()) {
						log.finer("Interrupted");
						close();
						return;
					}
					no++;
					ArrayList<Object> header = (isFirstRow ? new ArrayList<Object>()
							: null);
					ArrayList<Object> row = new ArrayList<Object>();
					int colOffset = 1; // columns start with 1
					int col = 0;
					for (MBrowseField field : browserFields) {

						if (isFirstRow) {
							String columnName = field.getName();
							header.add(columnName);
						}

						Object data = null;
						int colIndex = col + colOffset;
						if(field.isKey() && !field.getName().equals(field.getAD_View_Column().getColumnSQL().equals("'Row' AS \"Row\"")))
							data = new IDColumn(m_rs.getInt(colIndex));
						else if (field.isKey() && field.getName().equals(field.getAD_View_Column().getColumnSQL().equals("'Row' AS \"Row\"")))
							data = new IDColumn(no);
						else if (DisplayType.YesNo == field.getAD_Reference_ID())
							data = new Boolean("Y".equals(m_rs
									.getString(colIndex)));
						else if (DisplayType.isDate(field.getAD_Reference_ID()))
							data = m_rs.getTimestamp(colIndex);
						else if (DisplayType.isNumeric(field.getAD_Reference_ID()))
							data = m_rs.getBigDecimal(colIndex);
						else if (DisplayType.Integer == field.getAD_Reference_ID())
							data = new Integer(m_rs.getInt(colIndex));
						/*else if (c == KeyNamePair.class) {
							String display = m_rs.getString(colIndex);
							int key = m_rs.getInt(colIndex + 1);
							data = new KeyNamePair(key, display);
							colOffset++;
						}*/ else
							data = m_rs.getString(colIndex);

						row.add(data);
						col++;
					}

					if (isFirstRow)
						m_rows.add(header);
					m_rows.add(row);
					isFirstRow = false;
				}

			} catch (Throwable e) {
				log.log(Level.SEVERE, dataSql, e);
			} finally {
				close();
			}
			log.fine("#" + no + " - " + (System.currentTimeMillis() - start)
					+ "ms");
			if (no == 0)
				log.fine(dataSql);

		} // run

		private void close() {
			DB.close(m_rs, m_pstmt);
			m_rs = null;
			m_pstmt = null;
		}
	} // Exporter

	public int getWindowNo()
	{
		return windowNo;
	}
	
	/**
	 * BR [242 ]
	 * Get Window Number from parent window
	 * @return
	 */
	private int getParentWindowNo() {
		return parentWindowNo;
	}
}
