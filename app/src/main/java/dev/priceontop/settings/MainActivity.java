package dev.priceontop.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import dev.priceontop.core.ProviderType;
import dev.priceontop.storage.AndroidPrivatePriceStorage;
import dev.priceontop.R;

public class MainActivity extends Activity {

    private SettingsViewModel viewModel;

    private Switch switchEnable;
    private Spinner spinnerProvider;
    private EditText editSymbol;
    private EditText editApiKey;
    private EditText editRefreshInterval;
    private EditText editTimeout;
    private View layoutCustomProvider;
    private EditText editCustomUrl;
    private EditText editCustomJsonPrice;
    private EditText editCustomJsonSymbol;
    private EditText editCustomJsonCurrency;
    private EditText editCustomJsonTimestamp;
    private Button buttonSave;
    private Button buttonTest;
    private TextView textPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new SettingsViewModel(new AndroidPrivatePriceStorage(this));

        switchEnable = findViewById(R.id.switch_enable);
        spinnerProvider = findViewById(R.id.spinner_provider);
        editSymbol = findViewById(R.id.edit_symbol);
        editApiKey = findViewById(R.id.edit_api_key);
        editRefreshInterval = findViewById(R.id.edit_refresh_interval);
        editTimeout = findViewById(R.id.edit_timeout);
        layoutCustomProvider = findViewById(R.id.layout_custom_provider);
        editCustomUrl = findViewById(R.id.edit_custom_url);
        editCustomJsonPrice = findViewById(R.id.edit_custom_json_price);
        editCustomJsonSymbol = findViewById(R.id.edit_custom_json_symbol);
        editCustomJsonCurrency = findViewById(R.id.edit_custom_json_currency);
        editCustomJsonTimestamp = findViewById(R.id.edit_custom_json_timestamp);
        buttonSave = findViewById(R.id.button_save);
        buttonTest = findViewById(R.id.button_test);
        textPreview = findViewById(R.id.text_preview);

        setupProviderSpinner();
        bindViewModelToUi();

        buttonSave.setOnClickListener(v -> {
            bindUiToViewModel();
            if (viewModel.save()) {
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
                bindViewModelToUi();
                textPreview.setText(viewModel.getLastSuccessfulPreview() != null && !viewModel.getLastSuccessfulPreview().isEmpty() 
                    ? viewModel.getLastSuccessfulPreview() 
                    : "Saved.");
            } else {
                List<String> errors = viewModel.getErrors();
                String message = String.join("\n", errors);
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                textPreview.setText("Errors:\n" + message);
            }
        });

        buttonTest.setOnClickListener(v -> {
            bindUiToViewModel();
            String preview = viewModel.testProvider();
            textPreview.setText(preview);
        });
    }

    private void setupProviderSpinner() {
        ProviderType[] types = ProviderType.values();
        List<String> providerNames = new ArrayList<>();
        for (ProviderType type : types) {
            providerNames.add(type.name());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, providerNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProvider.setAdapter(adapter);

        spinnerProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                if (ProviderType.CUSTOM_JSON.name().equals(selected)) {
                    layoutCustomProvider.setVisibility(View.VISIBLE);
                } else {
                    layoutCustomProvider.setVisibility(View.GONE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                layoutCustomProvider.setVisibility(View.GONE);
            }
        });
    }

    private void bindViewModelToUi() {
        switchEnable.setChecked(viewModel.isEnabled());
        
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinnerProvider.getAdapter();
        if (adapter != null) {
            int position = adapter.getPosition(viewModel.getProvider());
            if (position >= 0) {
                spinnerProvider.setSelection(position);
            }
        }

        editSymbol.setText(viewModel.getSymbol());
        editApiKey.setText(viewModel.getDisplayApiKey());
        editRefreshInterval.setText(viewModel.getRefreshInterval());
        editTimeout.setText(viewModel.getTimeoutMillis());
        editCustomUrl.setText(viewModel.getCustomUrlTemplate());
        editCustomJsonPrice.setText(viewModel.getCustomJsonPathPrice());
        editCustomJsonSymbol.setText(viewModel.getCustomJsonPathSymbol());
        editCustomJsonCurrency.setText(viewModel.getCustomJsonPathCurrency());
        editCustomJsonTimestamp.setText(viewModel.getCustomJsonPathTimestamp());
    }

    private void bindUiToViewModel() {
        viewModel.setEnabled(switchEnable.isChecked());
        if (spinnerProvider.getSelectedItem() != null) {
            viewModel.setProvider(spinnerProvider.getSelectedItem().toString());
        }
        viewModel.setSymbol(editSymbol.getText().toString());
        viewModel.setApiKey(editApiKey.getText().toString());
        viewModel.setRefreshInterval(editRefreshInterval.getText().toString());
        viewModel.setTimeoutMillis(editTimeout.getText().toString());
        viewModel.setCustomUrlTemplate(editCustomUrl.getText().toString());
        viewModel.setCustomJsonPathPrice(editCustomJsonPrice.getText().toString());
        viewModel.setCustomJsonPathSymbol(editCustomJsonSymbol.getText().toString());
        viewModel.setCustomJsonPathCurrency(editCustomJsonCurrency.getText().toString());
        viewModel.setCustomJsonPathTimestamp(editCustomJsonTimestamp.getText().toString());
    }
}
